package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.apis.PlaidApi
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsGetRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsGetRequestOptions
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Batch sync runner.
 *
 * Handles the "batch" sync mode, which syncs a large batch of transactions at once, then exits.
 */
@ConditionalOnProperty(name = ["fireflyPlaidConnector2.syncMode"], havingValue = "batch")
@Component
class BatchSyncRunner(
    @Value("\${fireflyPlaidConnector2.maxSyncDays}")
    private val syncDays: Int,
    @Value("\${fireflyPlaidConnector2.plaid.batchSize}")
    private val plaidBatchSize: Int,

    private val plaidApi: PlaidApi,
    private val syncHelper: SyncHelper,

    private val converter: TransactionConverter,

    ) : Runner {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun run() {
        syncHelper.setApiCreds()

        val allPlaidTxs = mutableListOf<Transaction>()

        val startDate = LocalDate.now().minusDays(syncDays.toLong())
        val endDate = LocalDate.now()

        runBlocking {
            val (accountMap, accountAccessTokenSequence) = syncHelper.getAllPlaidAccessTokenAccountIdSets()
            for ((accessToken, accountIds) in accountAccessTokenSequence) {
                var offset = 0
                do {
                    /**
                     * Iterate through batches of Plaid transactions
                     *
                     * We're storing all this data in memory so we can try to match up offsetting transfers before inserting
                     *  into Firefly.
                     * Note that the heap size may need to be increased if you're handling a ton of transactions.
                     */
                    /**
                     * Iterate through batches of Plaid transactions
                     *
                     * We're storing all this data in memory so we can try to match up offsetting transfers before inserting
                     *  into Firefly.
                     * We don't use fireflyPlaidConnector2.transferMatchWindowDays here because if we did we'd have to
                     *  do some complex rolling window shenanigans that I have no interest in implementing, and it's
                     *  easy to run batch mode once on a high-spec machine.
                     * Note that the heap size may need to be increased if you're handling a ton of transactions.
                     */
                    val request = TransactionsGetRequest(
                        accessToken,
                        startDate,
                        endDate,
                        null,
                        TransactionsGetRequestOptions(
                            accountIds,
                            plaidBatchSize,
                            offset,
                            includeOriginalDescription = true,
                            includePersonalFinanceCategoryBeta = false,
                            includePersonalFinanceCategory = true,
                        )
                    )
                    val plaidTxs: List<Transaction>
                    try {
                        plaidTxs = plaidApi.transactionsGet(request).body().transactions
                    } catch (cre: ClientRequestException) {
                        logger.error("Error requesting Plaid transactions. Request: $request; ")
                        throw cre
                    }
                    allPlaidTxs.addAll(plaidTxs)

                    /**
                     * This would be where we query transactions from Firefly and look for dupes, but the Firefly
                     *  API doesn't have a way to query by external id and I don't think it's worth the effort to
                     *  do date range queries and sift through all transactions, so for now we'll rely on Firefly's
                     *  "duplicate hash" dupe checking mechanism.
                     */

                    offset += plaidTxs.size

                    // Keep going until we get all the transactions
                } while (plaidTxs.size == plaidBatchSize)
            }

            // Map Plaid transactions to Firefly transactions
            val fireflyTxs = converter.convertBatch(allPlaidTxs, accountMap)

            // Insert into Firefly
            syncHelper.optimisticInsertIntoFirefly(fireflyTxs)
        }
    }
}