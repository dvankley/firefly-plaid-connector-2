package net.djvk.fireflyPlaidConnector2.sync

import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeFilter
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Service for handling Firefly transaction operations.
 */
@Component
class FireflyTransactionService(
    private val fireflyTxApi: TransactionsApi,
    private val syncHelper: SyncHelper,
    private val fireflyApiWrapper: FireflyApiWrapper,
    private val replacementJournal: TransferReplacementJournal,
    
    @Value("\${fireflyPlaidConnector2.polled.existingFireflyPullWindowDays}")
    private val existingFireflyPullWindowDays: Int
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Fetches all Firefly transactions within the configured window.
     */
    suspend fun fetchExistingFireflyTransactions(): List<TransactionRead> {
        val existingFireflyTxs = mutableListOf<TransactionRead>()
        val transferWindowEnd = LocalDate.now()
        val transferWindowStart = transferWindowEnd.minusDays(existingFireflyPullWindowDays.toLong())

        var page = 1
        while (true) {
            logger.debug("Fetching page $page of Firefly transactions with window starting at $transferWindowStart")
            val response = fireflyApiWrapper.executeRequest("list Firefly transactions page $page") {
                fireflyTxApi.listTransaction(
                    page,
                    transferWindowStart,
                    transferWindowEnd,
                    TransactionTypeFilter.all,
                ).body()
            }
            logger.debug("Fetched ${response.data.size} recent Firefly transactions")

            val pagination = response.meta.pagination
            if (pagination == null) {
                check(page == 1) { "Missing Firefly pagination metadata for requested page $page" }
                return response.data
            }
            check(pagination.currentPage == page && pagination.totalPages >= page) {
                "Invalid Firefly pagination for requested page $page: received page ${pagination.currentPage} of ${pagination.totalPages}"
            }

            existingFireflyTxs.addAll(response.data)
            if (pagination.currentPage == pagination.totalPages) return existingFireflyTxs
            page = pagination.currentPage + 1
        }
    }

    suspend fun processFireflyCreatesAndTransfers(
        creates: List<FireflyTransactionDto>,
        transferUpdates: List<FireflyTransactionDto>,
        existing: List<TransactionRead>,
    ) {
        syncHelper.optimisticInsertBatchIntoFirefly(creates)
        processFireflyTransferUpdates(transferUpdates, existing)
    }

    /**
     * Firefly's transaction update endpoint does not allow changing transaction types
     *  (i.e. deposit to transfer), so in cases where we're trying to update existing
     *  Firefly non-transfer transactions (combined with an incoming Plaid create) to become
     *  transfer transactions, we have to resolve the updates as deletes and creates.
     * I'm not crazy about this because any other reference to the existing record will be
     *  broken, but such is life (and this behavior has been around for a while at this point).
     */
    private suspend fun processFireflyTransferUpdates(
        updates: List<FireflyTransactionDto>,
        existing: List<TransactionRead>,
    ) {
        if (updates.isEmpty()) return
        val originals = existing.associateBy { it.id }
        val replacements = updates.map { update ->
            val id = requireNotNull(update.id) { "Transfer replacement is missing its original Firefly ID" }
            val original = requireNotNull(originals[id]) { "Missing original Firefly transaction $id" }
            require(update.tx.amount.toBigDecimal().signum() > 0) { "Transfer replacement $id must have a positive amount" }
            require(!update.tx.externalId.isNullOrBlank()) { "Transfer replacement $id is missing its external ID" }
            require(update.tx.sourceId != null || !update.tx.sourceName.isNullOrBlank()) { "Missing transfer source for $id" }
            require(update.tx.destinationId != null || !update.tx.destinationName.isNullOrBlank()) {
                "Missing transfer destination for $id"
            }
            TransferReplacement(id, original.attributes.transactions.single(), update.toTransactionStore())
        }
        replacementJournal.append(replacements)
        for (replacement in replacements) reconcileTransferReplacement(replacement)
    }

    internal suspend fun recoverTransferReplacements(replacements: List<TransferReplacement>) {
        for (replacement in replacements) {
            if (!replacement.completed) reconcileTransferReplacement(replacement)
        }
    }

    private suspend fun reconcileTransferReplacement(entry: TransferReplacement) {
        val intended = entry.replacement.transactions.single()
        val externalId = requireNotNull(intended.externalId)
        val matches = syncHelper.findTransactionsByExternalId(externalId).filter { it.id != entry.originalId }
        if (matches.isNotEmpty()) {
            val actual = matches.singleOrNull()?.attributes?.transactions?.singleOrNull()
            check(
                actual != null && actual.type == TransactionTypeProperty.transfer &&
                    actual.amount.toBigDecimal().compareTo(intended.amount.toBigDecimal()) == 0 &&
                    (intended.sourceId?.let { it == actual.sourceId } ?: (intended.sourceName == actual.sourceName)) &&
                    (intended.destinationId?.let { it == actual.destinationId } ?:
                        (intended.destinationName == actual.destinationName))
            ) { "Conflicting Firefly transaction for transfer replacement $externalId" }
        }
        syncHelper.deleteBatchInFirefly(listOf(entry.originalId))
        if (matches.isEmpty()) syncHelper.insertTransferReplacement(entry.replacement)
        replacementJournal.complete(entry.originalId)
    }
}
