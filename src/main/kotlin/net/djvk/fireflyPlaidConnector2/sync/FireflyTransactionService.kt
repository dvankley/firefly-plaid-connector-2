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
    private val fireflyPageCountMax = 20

    /**
     * Fetches all Firefly transactions within the configured window.
     */
    suspend fun fetchExistingFireflyTransactions(): List<TransactionRead> {
        val existingFireflyTxs = mutableListOf<TransactionRead>()
        val transferWindowStart = LocalDate.now().minusDays(existingFireflyPullWindowDays.toLong())

        var fireflyTxPage = 0
        do {
            logger.debug("Fetching page $fireflyTxPage of Firefly transactions with window starting at $transferWindowStart")
            val page = fireflyTxPage++
            val response = fireflyApiWrapper.executeRequest("list Firefly transactions page $page") {
                fireflyTxApi.listTransaction(
                    page,
                    transferWindowStart,
                    LocalDate.now(),
                    TransactionTypeFilter.all,
                ).body()
            }
            val pagination = response.meta.pagination

            /**
             * Don't do any more filtering here, we will need all transactions for potentially matching
             *  up to update and delete requests.
             *
             * See [TransactionConverter.filterFireflyCandidateTransferTxs] for the filtering we do
             *  before trying to match up transfers.
             */
            val filteredTxs = response.data
            logger.debug("Fetched ${filteredTxs.size} existing Firefly single-split, non transfer transactions with window starting at $transferWindowStart")
            existingFireflyTxs.addAll(filteredTxs)
        } while (pagination != null &&
            pagination.currentPage < pagination.totalPages &&
            // This condition is a failsafe to avoid an infinite loop
            fireflyTxPage < fireflyPageCountMax
        )
        
        if (fireflyTxPage >= fireflyPageCountMax) {
            throw RuntimeException("Exceeded Firefly failsafe max page count $fireflyPageCountMax")
        }
        
        return existingFireflyTxs
    }

    /**
     * Processes transaction updates in Firefly.
     */
    suspend fun processFireflyTransactionUpdates(
        creates: List<FireflyTransactionDto>,
        updates: List<FireflyTransactionDto>,
        deletes: List<String>,
        existing: List<TransactionRead>,
    ) {
        // Insert new transactions
        syncHelper.optimisticInsertBatchIntoFirefly(creates)
        
        // Process updates
        /**
         * All updates here will either be updates of existing Firefly transactions that have been
         *  paired with incoming Plaid creates to become transfers, or updates coming in directly from Plaid.
         *
         * Split them here so we can handle them separately.
         */
        val (transferUpdates, nonTransferUpdates) = updates.partition { it.tx.type == TransactionTypeProperty.transfer }
        processFireflyTransferUpdates(transferUpdates, existing)
        processFireflyNonTransferUpdates(nonTransferUpdates)

        // Process deletes
        syncHelper.deleteBatchInFirefly(deletes)
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

    /**
     * Updates direct from Plaid will always be non-transfers (see comment a few lines down
     *  in [TransactionConverter.convertPollSync]) because we're currently not trying to handle
     *  the complexity of Plaid updates being applied to Firefly transfers (which themselves
     *  originated as two distinct Plaid transactions).
     * Because Plaid direct updates are not transfers, we can update them directly in Firefly.
     */
    private suspend fun processFireflyNonTransferUpdates(updates: List<FireflyTransactionDto>) {
        syncHelper.updateBatchInFirefly(updates)
    }
}
