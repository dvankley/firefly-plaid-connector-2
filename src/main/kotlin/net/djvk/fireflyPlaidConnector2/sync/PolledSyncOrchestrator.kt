package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.ApiService
import net.djvk.fireflyPlaidConnector2.api.ApiTransportException
import net.djvk.fireflyPlaidConnector2.api.withTransportFailure
import net.djvk.fireflyPlaidConnector2.config.PolledSyncProperties
import net.djvk.fireflyPlaidConnector2.transactions.FireflyAccountId
import net.djvk.fireflyPlaidConnector2.transactions.plaidExternalId
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

typealias PlaidSyncCursor = String

internal const val POLL_COMPLETE_LOG = "Poll complete. Sleeping for"
internal const val POLL_DEFERRED_LOG = "Polled sync paused after"

/**
 * Orchestrates the polled sync process.
 *
 * Handles the "polled" sync mode, which periodically polls for new transactions and processes them.
 * This class coordinates the different components involved in the sync process.
 */
class PolledSyncOrchestrator(
    private val properties: PolledSyncProperties,
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
    private val mainJob = AtomicReference<Job?>(null)
    private val pollInterval = properties.syncFrequencyMinutes.minutes

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

    override fun run() = run { delay(it) }

    internal fun run(waitForNextAttempt: suspend (Duration) -> Unit) = runBlocking {
        val job = launch(start = CoroutineStart.LAZY) { poll(waitForNextAttempt) }
        check(mainJob.compareAndSet(null, job)) { "Polled sync is already running" }
        try {
            if (terminated.get()) job.cancel()
            job.join()
        } finally {
            mainJob.compareAndSet(job, null)
        }
    }

    private suspend fun poll(waitForNextAttempt: suspend (Duration) -> Unit) {
        retryStartup(waitForNextAttempt) {
            ApiService.FIREFLY.withTransportFailure("version check") { syncHelper.setApiCreds() }
        }
        retryStartup(waitForNextAttempt) { initializeCursors() }

        val (accountMap, accountAccessTokenSequence) = syncHelper.getAllPlaidAccessTokenAccountIdSets()
        while (true) {
            val completed = attempt {
                logger.debug("Polling loop start")
                val cursorMap = cursorManager.readCursorMap()
                processTransactions(accountMap, accountAccessTokenSequence, cursorMap)
            }
            if (completed) {
                logger.trace("Calling System.gc()")
                System.gc()
                logger.info("$POLL_COMPLETE_LOG {} minutes", properties.syncFrequencyMinutes)
            }
            waitForNextAttempt(pollInterval)
        }
    }

    private suspend fun retryStartup(waitForNextAttempt: suspend (Duration) -> Unit, step: suspend () -> Unit) {
        while (!attempt(step)) {
            waitForNextAttempt(pollInterval)
        }
    }

    private suspend fun attempt(step: suspend () -> Unit): Boolean {
        currentCoroutineContext().ensureActive()
        try {
            step()
            return true
        } catch (failure: ApiTransportException) {
            currentCoroutineContext().ensureActive()
            logger.warn(
                "$POLL_DEFERRED_LOG {} {} failed ({}). Retrying in {} minutes.",
                failure.service.displayName, failure.operation, failure.cause?.javaClass?.simpleName,
                properties.syncFrequencyMinutes,
            )
            return false
        }
    }

    override fun destroy() {
        logger.info("Shutting down ${this::class}")
        terminated.set(true)
        mainJob.get()?.cancel()
    }
}
