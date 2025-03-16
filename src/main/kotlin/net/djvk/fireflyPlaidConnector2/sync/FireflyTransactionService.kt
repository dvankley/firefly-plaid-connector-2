package net.djvk.fireflyPlaidConnector2.sync

import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeFilter
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
            val response = fireflyTxApi.listTransaction(
                fireflyTxPage++,
                transferWindowStart,
                LocalDate.now(),
                TransactionTypeFilter.all,
            ).body()
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
        deletes: List<String>
    ) {
        // Insert new transactions
        syncHelper.optimisticInsertBatchIntoFirefly(creates)
        
        // Process updates
        for (update in updates) {
            /**
             * Firefly's transaction update endpoint does not allow changing transaction types
             *  (i.e. deposit to transfer), so we have to resolve updates as deletes and creates.
             * I'm not crazy about this because any other reference to the existing record will be
             *  broken, but such is life.
             */
            update.id ?: throw IllegalArgumentException("Unexpected update tx missing id: $update")
            
            /**
             * Delete first, if that fails, don't do the create.
             */
            try {
                syncHelper.deleteBatchInFirefly(listOf(update.id))
            } catch (e: Exception) {
                logger.error(
                    "Failed to execute delete as first part of updating transaction ${update.id}; " +
                            "aborting create part of update operation", e
                )
                continue
            }
            
            /**
             * This should not be a duplicate, so allow an exception to propagate if it is
             */
            syncHelper.pessimisticInsertBatchIntoFirefly(listOf(update))
        }
        
        // Process deletes
        syncHelper.deleteBatchInFirefly(deletes)
    }
}