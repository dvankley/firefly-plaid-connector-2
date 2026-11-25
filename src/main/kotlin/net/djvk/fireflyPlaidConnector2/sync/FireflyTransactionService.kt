package net.djvk.fireflyPlaidConnector2.sync

import net.djvk.fireflyPlaidConnector2.api.ApiService.FIREFLY
import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeFilter
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.withTransportFailure
import net.djvk.fireflyPlaidConnector2.config.PolledSyncProperties
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter.TransferUpdate
import net.djvk.fireflyPlaidConnector2.transactions.plaidTransactionId
import net.djvk.fireflyPlaidConnector2.transactions.plaidTransferReference
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * Service for handling Firefly transaction operations.
 */
class FireflyTransactionService(
    private val fireflyTxApi: TransactionsApi,
    private val syncHelper: SyncHelper,
    private val fireflyApiWrapper: FireflyApiWrapper,
    private val replacementJournal: TransferReplacementJournal,
    private val properties: PolledSyncProperties,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Fetches all Firefly transactions within the configured window.
     */
    suspend fun fetchExistingFireflyTransactions(): List<TransactionRead> {
        val existingFireflyTxs = mutableListOf<TransactionRead>()
        val transferWindowEnd = LocalDate.now()
        val transferWindowStart = transferWindowEnd.minusDays(properties.existingFireflyPullWindowDays.toLong())

        var page = 1
        while (true) {
            logger.debug("Fetching page $page of Firefly transactions with window starting at $transferWindowStart")
            val response = FIREFLY.withTransportFailure("list transactions") {
                fireflyApiWrapper.executeRequest("list Firefly transactions page $page") {
                    fireflyTxApi.listTransaction(
                        page,
                        transferWindowStart,
                        transferWindowEnd,
                        TransactionTypeFilter.all,
                    ).body()
                }
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
        transferUpdates: List<TransferUpdate>,
        existing: List<TransactionRead>,
    ) {
        FIREFLY.withTransportFailure("create transactions") {
            syncHelper.optimisticInsertBatchIntoFirefly(creates)
        }
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
        updates: List<TransferUpdate>,
        existing: List<TransactionRead>,
    ) {
        if (updates.isEmpty()) return
        val originals = existing.associateBy { it.id }
        val replacements = updates.map { update ->
            val id = requireNotNull(update.transaction.id) { "Transfer replacement is missing its original Firefly ID" }
            val original = requireNotNull(originals[id]) { "Missing original Firefly transaction $id" }
            TransferReplacement(
                originalId = id,
                original = original.attributes.transactions.single(),
                replacement = update.transaction.toTransactionStore(),
                incomingPlaidTransactionId = update.incomingPlaidTransactionId,
            ).also { validateTransferReplacement(it) }
        }
        replacementJournal.append(replacements)
        for (replacement in replacements) reconcileTransferReplacement(replacement)
    }

    internal suspend fun recoverTransferReplacements(replacements: List<TransferReplacement>) {
        for (replacement in replacements) {
            if (!replacement.completed) reconcileTransferReplacement(replacement)
        }
    }

    private fun validateTransferReplacement(entry: TransferReplacement): TransactionSplit {
        val id = entry.originalId
        val intended = entry.replacement.transactions.single()
        require(intended.type == TransactionTypeProperty.transfer) { "Replacement for $id must be a transfer" }
        require(intended.amount.toBigDecimalOrNull()?.signum() == 1) { "Transfer replacement $id must have a positive amount" }
        val sourceId = intended.sourceId?.toLongOrNull()
        val destinationId = intended.destinationId?.toLongOrNull()
        require(sourceId != null && sourceId > 0) { "Transfer replacement $id requires a valid source account ID" }
        require(destinationId != null && destinationId > 0) { "Transfer replacement $id requires a valid destination account ID" }
        require(sourceId != destinationId) { "Transfer replacement $id requires distinct account IDs" }

        val incomingId = requireNotNull(plaidTransactionId(intended.externalId)) {
            "Transfer replacement $id is missing its Plaid external ID"
        }
        // Older journal entries only stored the payload's external ID.
        require(entry.incomingPlaidTransactionId == null || entry.incomingPlaidTransactionId == incomingId) {
            "Transfer replacement $id has the wrong Plaid external ID"
        }
        val originalId = plaidTransactionId(entry.original.externalId)
        if (originalId != null) {
            require(originalId != incomingId) { "Transfer replacement $id requires distinct Plaid leg IDs" }
            require(intended.internalReference == plaidTransferReference(originalId)) {
                "Transfer replacement $id is missing the original Plaid leg reference"
            }
        }
        return intended
    }

    private suspend fun reconcileTransferReplacement(entry: TransferReplacement) {
        val intended = validateTransferReplacement(entry)
        val externalId = requireNotNull(intended.externalId)
        val matches = FIREFLY.withTransportFailure("find transfer replacement") {
            syncHelper.findTransactionsByExternalId(externalId)
        }.filter { it.id != entry.originalId }
        if (matches.isNotEmpty()) {
            val actual = matches.singleOrNull()?.attributes?.transactions?.singleOrNull()
            check(
                actual != null && actual.type == TransactionTypeProperty.transfer &&
                    actual.amount.toBigDecimal().compareTo(intended.amount.toBigDecimal()) == 0 &&
                    intended.sourceId == actual.sourceId && intended.destinationId == actual.destinationId &&
                    (intended.internalReference == null || intended.internalReference == actual.internalReference)
            ) { "Conflicting Firefly transaction for transfer replacement $externalId" }
        }
        FIREFLY.withTransportFailure("delete original transaction") {
            syncHelper.deleteBatchInFirefly(listOf(entry.originalId))
        }
        if (matches.isEmpty()) {
            FIREFLY.withTransportFailure("create transfer replacement") {
                syncHelper.insertTransferReplacement(entry.replacement)
            }
        }
        replacementJournal.complete(entry.originalId)
    }
}
