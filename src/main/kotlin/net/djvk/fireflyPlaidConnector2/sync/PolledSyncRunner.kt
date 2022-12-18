package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.*
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.CategoriesApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AccountRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeFilter
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
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
    @Value("\${fireflyPlaidConnector2.polled.syncFrequencyMinutes}")
    private val syncFrequencyMinutes: IntervalMinutes,
    @Value("\${fireflyPlaidConnector2.polled.existingFireflyPullWindowDays}")
    private val existingFireflyPullWindowDays: Int,
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
                logger.debug("Beginning Plaid sync endpoint cursor initialization")
                for ((accessToken, _) in accountAccessTokenSequence) {
                    /**
                     * If we already have a cursor for this access token, then move on
                     */
                    if (cursorMap.contains(accessToken)) {
                        logger.debug("Cursor map contains $accessToken, skipping initialization for it")
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
                                    "Updating cursor map to next cursor: ${response.nextCursor}"
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
                    logger.debug("Polling loop start")
                    val transferWindowStart = LocalDate.now().minusDays(existingFireflyPullWindowDays.toLong())

                    /**
                     * Get all Firefly transactions within [existingFireflyPullWindowDays] so that we can handle Plaid
                     *  updates and deletes, as well as try to match up transfers.
                     */
                    val existingFireflyTxs = mutableListOf<TransactionRead>()

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

                    val plaidCreatedTxs = mutableListOf<PlaidTransaction>()
                    val plaidUpdatedTxs = mutableListOf<PlaidTransaction>()
                    val plaidDeletedTxs = mutableListOf<PlaidTransactionId>()

                    for ((accessToken, accountIds) in accountAccessTokenSequence) {
                        logger.debug("Querying Plaid transaction sync endpoint for access token $accessToken " +
                            " and account ids ${accountIds.joinToString("; ")}")
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
                    /**
                     * Don't write the cursor map here, wait until after we've successfully committed the transactions
                     *  to Firefly so that we retry if something goes wrong with the Firefly insert
                     */

                    // Map Plaid transactions to Firefly transactions
                    logger.trace("Converting Plaid transactions to Firefly transactions")
                    // TODO: do we need to dedupe the create/update/delete events and keep only the latest event?
                    //  Or has Plaid already done that for us?
                    val convertResult = converter.convertPollSync(
                        accountMap,
                        plaidCreatedTxs,
                        plaidUpdatedTxs,
                        plaidDeletedTxs,
                        existingFireflyTxs,
                    )
                    logger.debug("Conversion result: ${convertResult.creates.size} creates; " +
                            "${convertResult.updates.size} updates; " +
                            "${convertResult.deletes.size} deletes;")

                    // Insert into Firefly
                    syncHelper.optimisticInsertBatchIntoFirefly(convertResult.creates)
                    for (update in convertResult.updates) {
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
                    syncHelper.deleteBatchInFirefly(convertResult.deletes)

                    /**
                     * Now that we've committed the changes to Firefly, update the cursor map
                     */
                    writeCursorMap(cursorMap)

                    /**
                     * Trigger GC to try to reduce heap size (depends on VM configuration)
                     *
                     * The application sits idle for the vast majority of its executing time in polling mode, so here
                     *  we try to trigger the GC before sleeping to hopefully reclaim some heap.
                     * This depends a lot on the VM and GC configuration.
                     * TODO: do some GC tests and give some guidance here.
                     */
                    logger.trace("Calling System.gc()")
                    System.gc()

                    // Sleep until next poll
                    logger.info("Sleeping $syncFrequencyMinutes")
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
        logger.trace("Reading Plaid sync cursor map from file")
        return withContext(Dispatchers.IO) {
            val file = cursorFilePath.toFile()

            if (!file.exists()) {
                logger.trace("No existing Plaid sync cursor map found, starting from scratch")
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
        logger.trace("Writing ${map.size} Plaid sync cursors to map file")
        return withContext(Dispatchers.IO) {
            cursorFilePath
                .toFile()
                .writeText(
                    map.entries
                        .filter { it.value != "" }
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