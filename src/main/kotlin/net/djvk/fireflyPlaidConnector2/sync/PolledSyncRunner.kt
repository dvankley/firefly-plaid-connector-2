package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.*
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeFilter
import net.djvk.fireflyPlaidConnector2.api.plaid.apis.PlaidApi
import net.djvk.fireflyPlaidConnector2.api.plaid.models.*
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

typealias IntervalMinutes = Int
typealias PlaidSyncCursor = String

/**
 * Polled sync runner.
 *
 * Handles the "polled" sync mode, which periodically polls for new transactions and processes them.
 */
@ConditionalOnProperty(name = ["fireflyPlaidConnector2.syncMode"], havingValue = "polled")
@Component
class PolledSyncRunner(
    @Value("\${fireflyPlaidConnector2.syncFrequencyMinutes}")
    private val syncFrequencyMinutes: IntervalMinutes,
    @Value("\${fireflyPlaidConnector2.transferMatchWindowDays}")
    private val transferMatchWindowDays: Int,
    @Value("\${fireflyPlaidConnector2.plaid.batchSize}")
    private val plaidBatchSize: Int,

    private val plaidApi: PlaidApi,
    private val fireflyTxApi: TransactionsApi,
    private val syncHelper: SyncHelper,

    private val converter: TransactionConverter,
) : Runner, DisposableBean {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val fireflyPageCountMax = 20

    private val terminated = AtomicBoolean(false)
    private lateinit var mainJob: Job

    override fun run() {
        syncHelper.setApiCreds()

        runBlocking {
            mainJob = launch {
                val cursorMap = readCursorMap()
                val (accountMap, accountAccessTokenSequence) = syncHelper.getAllPlaidAccessTokenAccountIdSets()
                for ((accessToken, _) in accountAccessTokenSequence) {
                    /**
                     * If we already have a cursor for this access token, then move on
                     */
                    if (cursorMap.contains(accessToken)) {
                        continue
                    }
                    /**
                     * For access tokens that we don't have cursors for, iterate through historical data and ignore it
                     *  to get current cursors
                     */
                    do {
                        val response =
                            executeTransactionSyncRequest(accessToken, cursorMap[accessToken], plaidBatchSize)
                        logger.debug(
                            "Received initial batch of sync updates for access token $accessToken. " +
                                    "Next cursor ${response.nextCursor}"
                        )
                        if (response.nextCursor.isNotBlank()) {
                            cursorMap[accessToken] = response.nextCursor
                        }
                    } while (response.hasMore)
                }
                writeCursorMap(cursorMap)
                // TODO: Ensure the Dockerfile provides a volume for the cursor map

                /**
                 * Periodic polling loop
                 */
                do {
                    val transferWindowStart = LocalDate.now().minusDays(transferMatchWindowDays.toLong())

                    /**
                     * Get all Firefly transactions within [transferMatchWindowDays] so that we can try to match up transfers.
                     */
                    val existingFireflyTxs = mutableListOf<FireflyTransactionDto>()

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
                         * Filter out split transactions; we're not going to bother trying to match those up as transfers
                         */
                        existingFireflyTxs.addAll(response.data
                            .filter { it.attributes.transactions.size == 1 }
                            .map { FireflyTransactionDto(it.id, it.attributes.transactions.first()) }
                        )
                        logger.debug("Fetched ${response.data.size} existing Firefly single-split transactions with window starting at $transferWindowStart")
                    } while (pagination != null &&
                        pagination.currentPage < pagination.totalPages &&
                        // This condition is a failsafe to avoid an infinite loop
                        fireflyTxPage < fireflyPageCountMax
                    )
                    if (fireflyTxPage >= fireflyPageCountMax) {
                        throw RuntimeException("Exceeded Firefly failsafe max page count $fireflyPageCountMax")
                    }

                    val plaidCreatedTxs = mutableListOf<PlaidTransaction>()
                    val plaidUpdatedTxs = mutableListOf<PlaidTransaction>()
                    val plaidDeletedTxs = mutableListOf<PlaidTransactionId>()

                    for ((accessToken, accountIds) in accountAccessTokenSequence) {
                        val accountIdSet = accountIds.toSet()
                        /**
                         * Plaid transaction batch loop
                         */
                        do {
                            /**
                             * Iterate through batches of Plaid transactions
                             *
                             * In sync mode we fetch and retain all Plaid transactions that have changed since the last poll.
                             */
                            val response = executeTransactionSyncRequest(
                                accessToken,
                                cursorMap[accessToken],
                                plaidBatchSize
                            )
                            cursorMap[accessToken] = response.nextCursor
                            logger.debug(
                                "Received batch of sync updates for access token $accessToken: " +
                                        "${response.added.size} created; ${response.modified.size} updated; " +
                                        "${response.removed.size} deleted; next cursor ${response.nextCursor}"
                            )

                            /**
                             * The transaction sync endpoint doesn't take accountId as a parameter, so do that filtering here
                             */
                            plaidCreatedTxs.addAll(response.added.filter { accountIdSet.contains(it.accountId) })
                            plaidUpdatedTxs.addAll(response.modified.filter { accountIdSet.contains(it.accountId) })
                            plaidDeletedTxs.addAll(response.removed.mapNotNull { it.transactionId })

                            // Keep going until we get all the transactions
                        } while (response.hasMore)
                    }
                    // Map Plaid transactions to Firefly transactions
                    // TODO: do we need to dedupe the create/update/delete events and keep only the latest event?
                    //  Or has Plaid already done that for us?
                    val convertResult = converter.convertPollSync(
                        accountMap,
                        plaidCreatedTxs,
                        plaidUpdatedTxs,
                        plaidDeletedTxs,
                        existingFireflyTxs,
                    )
//
                    // Insert into Firefly
                    syncHelper.optimisticInsertIntoFirefly(convertResult.creates)
                    syncHelper.updateIntoFirefly(convertResult.updates)
                    syncHelper.deleteInFirefly(convertResult.deletes)

                    /**
                     * Trigger GC to try to reduce heap size (depends on VM configuration)
                     *
                     * The application sits idle for the vast majority of its executing time in polling mode, so here
                     *  we try to trigger the GC before sleeping to hopefully reclaim some heap.
                     * This depends a lot on the VM and GC configuration.
                     * TODO: do some GC tests and give some guidance here.
                     */
                    System.gc()

                    // Sleep until next poll
                    delay(syncFrequencyMinutes.minutes)
                } while (!terminated.get())
            }
        }
    }

    val cursorFilePath = Path("persistence/plaid_sync_cursors.txt")

    /**
     * Reads the cursor map from file storage, if it exists.
     * If it doesn't exist, returns an empty map.
     */
    suspend fun readCursorMap(): MutableMap<PlaidAccessToken, PlaidSyncCursor> {
        return withContext(Dispatchers.IO) {
            val file = cursorFilePath.toFile()

            if (!file.exists()) {
                return@withContext mutableMapOf()
            }

            file
                .readLines()
                .associate { line ->
                    val (first, second) = line.split("|")
                    Pair(first, second)
                }
                .toMutableMap()
        }
    }

    /**
     * Writes the cursor map to file storage.
     */
    suspend fun writeCursorMap(map: Map<PlaidAccessToken, PlaidSyncCursor>) {
        return withContext(Dispatchers.IO) {
            cursorFilePath
                .toFile()
                .writeText(
                    map.entries
                        .joinToString("\n") { (token, cursor) ->
                            "$token|$cursor"
                        }
                )
        }
    }

    fun getTransactionSyncRequest(
        accessToken: PlaidAccessToken,
        cursor: PlaidSyncCursor?,
        plaidBatchSize: Int
    ): TransactionsSyncRequest {
        return TransactionsSyncRequest(
            accessToken,
            null,
            null,
            cursor,
            plaidBatchSize,
            TransactionsSyncRequestOptions(
                includeOriginalDescription = true,
                includePersonalFinanceCategory = true,
            )
        )
    }

    suspend fun executeTransactionSyncRequest(
        accessToken: PlaidAccessToken,
        cursor: PlaidSyncCursor?,
        plaidBatchSize: Int
    ): TransactionsSyncResponse {
        val request = getTransactionSyncRequest(accessToken, cursor, plaidBatchSize)
        try {
            return plaidApi.transactionsSync(request).body()
        } catch (cre: ClientRequestException) {
            logger.error("Error requesting Plaid transactions. Request: $request; ")
            throw cre
        }
    }

    override fun destroy() {
        logger.info("Shutting down ${this::class}")
        terminated.set(true)
        mainJob.cancel()
    }
}