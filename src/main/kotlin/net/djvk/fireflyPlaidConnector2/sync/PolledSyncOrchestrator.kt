package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


import net.djvk.fireflyPlaidConnector2.constants.*
import net.djvk.fireflyPlaidConnector2.constants.FireflyAccountId
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

import java.time.Clock
import java.time.Duration

import net.djvk.fireflyPlaidConnector2.constants.IntervalMinutes
import net.djvk.fireflyPlaidConnector2.constants.ResultCallbackUrl
import net.djvk.fireflyPlaidConnector2.constants.ResultCallbackBearerToken

/**
 * Orchestrates the polled sync process.
 *
 * Handles the "polled" sync mode, which periodically polls for new transactions and processes them.
 * This class coordinates the different components involved in the sync process.
 */
@ConditionalOnProperty(name = ["fireflyPlaidConnector2.syncMode"], havingValue = "polled")
@Component
class PolledSyncOrchestrator(
    @Value("\${fireflyPlaidConnector2.polled.syncFrequencyMinutes}")
    private val syncFrequencyMinutes: IntervalMinutes,

    @Value("\${fireflyPlaidConnector2.polled.resultCallbackUrl:}")
    private val resultCallbackUrl: ResultCallbackUrl,

    @Value("\${fireflyPlaidConnector2.polled.resultCallbackBearerToken:}")
    private val resultCallbackBearerToken: ResultCallbackBearerToken,

    private val syncHelper: SyncHelper,
    private val cursorManager: CursorManager,
    private val plaidSyncService: PlaidSyncService,
    private val fireflyTransactionService: FireflyTransactionService,
    private val converter: TransactionConverter,
) : Runner, DisposableBean {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val terminated = AtomicBoolean(false)
    private lateinit var mainJob: Job

    private val webHookService = WebhookService(resultCallbackUrl, resultCallbackBearerToken)

    /**
     * Initializes cursors for access tokens that don't have one yet.
     */
    suspend fun initializeCursors() {
        // Read cursor map from storage
        val cursorMap = cursorManager.readCursorMap()

        // Get account mappings
        val (accountMap, accountAccessTokenSequence) = syncHelper.getAllPlaidAccessTokenAccountIdSets()

        // Initialize cursors for access tokens that don't have one yet
        plaidSyncService.initializeCursors(accountAccessTokenSequence, cursorMap)
        cursorManager.writeCursorMap(cursorMap)
    }

    /**
     * Processes transactions by fetching from Plaid, converting, and updating Firefly.
     */
    suspend fun processTransactions(
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        accountAccessTokenSequence: Sequence<Pair<PlaidAccessToken, List<PlaidAccountId>>>,
        cursorMap: MutableMap<PlaidAccessToken, PlaidSyncCursor>
    ) {
        // Fetch existing Firefly transactions
        val existingFireflyTxs = fireflyTransactionService.fetchExistingFireflyTransactions()

        // Process Plaid transactions
        val plaidTransactions = plaidSyncService.processPlaidTransactions(
            accountAccessTokenSequence,
            cursorMap
        )

        // Convert Plaid transactions to Firefly format
        logger.debug("Converting plaidTransactions transactions to Firefly ${existingFireflyTxs.size} transactions")
        val convertResult = converter.convertPollSync(
            accountMap,
            plaidTransactions.created,
            plaidTransactions.updated,
            plaidTransactions.deleted,
            existingFireflyTxs,
        )
        logger.debug(
            "Conversion result: ${convertResult.creates.size} creates; " +
                    "${convertResult.updates.size} updates; " +
                    "${convertResult.deletes.size} deletes;"
        )

        // Process transaction updates in Firefly
        fireflyTransactionService.processFireflyTransactionUpdates(
            convertResult.creates,
            convertResult.updates,
            convertResult.deletes
        )

        // Update cursor map after successful processing
        cursorManager.writeCursorMap(cursorMap)

        if (webHookService.enabled()){
            try {
                webHookService.addDataForHook(
                    existingFireflyTxs,
                    plaidTransactions,
                    convertResult.creates,
                    convertResult.updates,
                    convertResult.deletes
                )
            }catch(e: Exception){
                logger.error("Failed Adding Data for Webhook: $e")
            }finally{
                logger.debug("Called DataForHook")
            }
        }else{
            logger.trace("Web Service Not Enabled.  No data provided")
        }


    }

    override fun run() {
        runBlocking {
            syncHelper.setApiCreds()

            mainJob = launch {
                // Initialize cursors
                initializeCursors()

                // Get account mappings for the polling loop
                val (accountMap, accountAccessTokenSequence) = syncHelper.getAllPlaidAccessTokenAccountIdSets()
                val cursorMap = cursorManager.readCursorMap()
                val clock = Clock.systemUTC()
                /**
                 * Periodic polling loop
                 */
                do {
                    logger.debug("Polling loop start")
                    val loop_start = clock.instant()

                    // Process transactions
                    processTransactions(accountMap, accountAccessTokenSequence, cursorMap)

                    // Trigger GC to try to reduce heap size
                    logger.trace("Calling System.gc()")
                    System.gc()

                    // If configured, report to callback
                    if (webHookService.enabled()){
                        try {
                            webHookService.post(Duration.between(loop_start, clock.instant()))
                        } catch (e: Exception){
                            logger.error("Failed to post results to $resultCallbackUrl: $e")
                        }
                    }


                    // Sleep until next poll
                    logger.info("Sleeping $syncFrequencyMinutes")
                    delay(syncFrequencyMinutes.minutes)
                } while (!terminated.get())
            }
        }
    }

    override fun destroy() {
        logger.info("Shutting down ${this::class}")
        terminated.set(true)
        mainJob.cancel()
    }
}
