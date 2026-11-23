package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.transactions.FireflyAccountId
import net.djvk.fireflyPlaidConnector2.transactions.plaidExternalId
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

typealias IntervalMinutes = Int
typealias PlaidSyncCursor = String

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

    private val syncHelper: SyncHelper,
    private val cursorManager: CursorManager,
    private val plaidSyncService: PlaidSyncService,
    private val fireflyTransactionService: FireflyTransactionService,
    private val converter: TransactionConverter,
    private val replacementJournal: TransferReplacementJournal,
    private val correctionQueue: PlaidCorrectionQueue,
) : Runner, DisposableBean {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val terminated = AtomicBoolean(false)
    private lateinit var mainJob: Job

    /**
     * Initializes cursors for access tokens that don't have one yet.
     */
    suspend fun initializeCursors() {
        // Read cursor map from storage
        val cursorMap = cursorManager.readCursorMap()
        // Recover any unfinished poll before writing initialization cursors.
        if (replacementJournal.pendingFor(cursorMap).isNotEmpty()) return

        // Get account mappings
        val (_, accountAccessTokenSequence) = syncHelper.getAllPlaidAccessTokenAccountIdSets()

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
        val pending = replacementJournal.pendingFor(cursorMap)
        val nextCursors = cursorMap.toMutableMap()
        fireflyTransactionService.recoverTransferReplacements(pending)
        val recoveredExternalIds = pending.flatMap {
            listOfNotNull(it.original.externalId, it.replacement.transactions.single().externalId)
        }.toSet()

        val plaidTransactions = plaidSyncService.processPlaidTransactions(
            accountAccessTokenSequence,
            nextCursors,
            requireComplete = pending.isNotEmpty(),
        )

        val additions = plaidTransactions.created.filter { plaidExternalId(it.transactionId) !in recoveredExternalIds }
        if (additions.isNotEmpty()) {
            val existingFireflyTxs = fireflyTransactionService.fetchExistingFireflyTransactions()
            logger.trace("Converting Plaid transactions to Firefly transactions")
            val convertResult = converter.convertPollSync(accountMap, additions, existingFireflyTxs)
            logger.debug("Conversion result: {} creates, {} transfer updates", convertResult.creates.size, convertResult.updates.size)
            fireflyTransactionService.processFireflyCreatesAndTransfers(
                convertResult.creates,
                convertResult.updates,
                existingFireflyTxs,
            )
        }
        correctionQueue.process(accountMap, plaidTransactions, cursorMap, nextCursors)

        // Update cursor map after successful processing
        val hasReplacements = replacementJournal.prepareCursorCommit(nextCursors)
        cursorManager.writeCursorMap(nextCursors)
        cursorMap.putAll(nextCursors)
        if (hasReplacements) replacementJournal.finishCursorCommit()
        correctionQueue.finishCursorCommit(nextCursors)
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

                /**
                 * Periodic polling loop
                 */
                do {
                    logger.debug("Polling loop start")

                    // Process transactions
                    processTransactions(accountMap, accountAccessTokenSequence, cursorMap)

                    // Trigger GC to try to reduce heap size
                    logger.trace("Calling System.gc()")
                    System.gc()

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
