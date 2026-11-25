package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.djvk.fireflyPlaidConnector2.api.ApiService
import net.djvk.fireflyPlaidConnector2.api.ApiTransportException
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.plaid.models.RemovedTransaction
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.testPolledProperties
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.NoRouteToHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes

/**
 * Test for the PolledSyncOrchestrator class.
 * 
 * This test verifies that the orchestrator correctly coordinates the different components
 * involved in the polled sync process.
 */
internal class PolledSyncOrchestratorTest {

    private val syncHelper: SyncHelper = mock()
    private val cursorManager: CursorManager = mock()
    private val plaidSyncService: PlaidSyncService = mock()
    private val fireflyTransactionService: FireflyTransactionService = mock()
    private val converter: TransactionConverter = mock()
    private val replacementJournal: TransferReplacementJournal = mock()
    private val correctionQueue: PlaidCorrectionQueue = mock()
    private val orchestrator = PolledSyncOrchestrator(
        testPolledProperties(), syncHelper, cursorManager, plaidSyncService, fireflyTransactionService, converter,
        replacementJournal, correctionQueue,
    )
    private val accountMap = mapOf("account1" to 1)
    private val accountAccessTokenSequence = sequenceOf("token1" to listOf("account1"))
    private val initialCursors = mapOf("token1" to "before")
    private val nextCursors = mapOf("token1" to "after")
    private val addition = PlaidFixtures.getPaymentTransaction(accountId = "account1", transactionId = "created1")

    @BeforeEach
    fun stubJournal() {
        runBlocking {
            whenever(replacementJournal.pendingFor(any())).thenReturn(emptyList())
            whenever(replacementJournal.prepareCursorCommit(any())).thenReturn(false)
        }
    }

    /**
     * Test that the orchestrator correctly initializes cursors.
     * 
     * This test verifies that the orchestrator reads the cursor map, gets account mappings,
     * and initializes cursors for access tokens that don't have one yet.
     */
    @Test
    fun testInitializeCursors() = runBlocking {
        val cursorMap = mutableMapOf<PlaidAccessToken, PlaidSyncCursor>()
        whenever(cursorManager.readCursorMap()).thenReturn(cursorMap)
        whenever(syncHelper.getAllPlaidAccessTokenAccountIdSets()).thenReturn(Pair(accountMap, accountAccessTokenSequence))

        orchestrator.initializeCursors()

        verify(cursorManager).readCursorMap()
        verify(syncHelper).getAllPlaidAccessTokenAccountIdSets()
        verify(plaidSyncService).initializeCursors(eq(accountAccessTokenSequence), eq(cursorMap))
        verify(cursorManager).writeCursorMap(eq(cursorMap))
    }

    @Test
    fun `retries each startup step before processing the first poll`() = runBlocking {
        val events = mutableListOf<String>()
        var saved = emptyMap<PlaidAccessToken, PlaidSyncCursor>()
        whenever(cursorManager.readCursorMap()).thenAnswer { saved.toMutableMap() }
        whenever(cursorManager.writeCursorMap(any())).thenAnswer {
            saved = it.getArgument<Map<PlaidAccessToken, PlaidSyncCursor>>(0).toMap()
            Unit
        }
        whenever(syncHelper.getAllPlaidAccessTokenAccountIdSets()).thenReturn(accountMap to accountAccessTokenSequence)
        whenever(syncHelper.setApiCreds()).thenAnswer {
            events += "unreachable Firefly"
            throw NoRouteToHostException()
        }.thenAnswer {
            events += "Firefly ready"
            Unit
        }
        whenever(plaidSyncService.initializeCursors(eq(accountAccessTokenSequence), any())).thenAnswer {
            events += "unreachable Plaid"
            throw ApiTransportException(ApiService.PLAID, "transactions sync", IOException())
        }.thenAnswer {
            events += "Plaid ready"
            it.getArgument<MutableMap<PlaidAccessToken, PlaidSyncCursor>>(1).putAll(initialCursors)
        }
        stagePlaidChanges(PlaidTransactionResult(emptyList(), emptyList(), emptyList()))

        var waits = 0
        orchestrator.run { interval ->
            assertEquals(30.minutes, interval)
            events += "wait"
            if (++waits == 3) orchestrator.destroy()
        }

        assertEquals(
            listOf("unreachable Firefly", "wait", "Firefly ready", "unreachable Plaid", "wait", "Plaid ready", "wait"),
            events,
        )
        assertEquals(nextCursors, saved)
    }

    @Test
    fun `replays the same delta when the Firefly scan fails after Plaid stages cursors`() = runBlocking {
        val changes = PlaidTransactionResult(listOf(addition), emptyList(), emptyList())
        stagePlaidChanges(changes)
        whenever(cursorManager.readCursorMap()).thenAnswer { initialCursors.toMutableMap() }
        whenever(syncHelper.getAllPlaidAccessTokenAccountIdSets()).thenReturn(accountMap to accountAccessTokenSequence)
        val outage = ApiTransportException(ApiService.FIREFLY, "list transactions", NoRouteToHostException())
        whenever(fireflyTransactionService.fetchExistingFireflyTransactions()).thenThrow(outage).thenReturn(emptyList())
        whenever(converter.convertPollSync(accountMap, changes.created, emptyList()))
            .thenReturn(TransactionConverter.ConvertPollSyncResult(emptyList(), emptyList()))

        var waits = 0
        orchestrator.run { interval ->
            assertEquals(30.minutes, interval)
            if (++waits == 1) {
                verify(cursorManager, never()).writeCursorMap(eq(nextCursors))
            } else {
                orchestrator.destroy()
            }
        }

        verify(fireflyTransactionService, times(2)).fetchExistingFireflyTransactions()
        verify(cursorManager).writeCursorMap(eq(nextCursors))
    }

    @Test
    fun `a cursor read failure escapes the polling runner`() = runBlocking {
        val failure = IOException("Could not read cursor file")
        whenever(cursorManager.readCursorMap()).thenReturn(initialCursors.toMutableMap()).thenAnswer { throw failure }
        whenever(syncHelper.getAllPlaidAccessTokenAccountIdSets()).thenReturn(accountMap to accountAccessTokenSequence)

        val thrown = assertFailsWith<IOException> { orchestrator.run { error("Unexpected poll delay") } }

        assertSame(failure, thrown)
        verify(plaidSyncService, never()).processPlaidTransactions(any(), any(), any())
    }

    @Test
    fun `destroy cancels an outage wait during startup`() = runBlocking {
        whenever(syncHelper.setApiCreds()).thenAnswer { throw NoRouteToHostException() }
        val awaitingNextAttempt = CompletableDeferred<Unit>()
        val runner = async(Dispatchers.Default) {
            orchestrator.run { interval ->
                assertEquals(30.minutes, interval)
                awaitingNextAttempt.complete(Unit)
                awaitCancellation()
            }
        }

        try {
            withTimeout(5_000) { awaitingNextAttempt.await() }
        } finally {
            orchestrator.destroy()
        }

        withTimeout(5_000) { runner.await() }
        verifyNoInteractions(cursorManager, plaidSyncService)
    }

    @Test
    fun `checks Plaid before scanning Firefly for new additions`() = runBlocking {
        val cursorMap = initialCursors.toMutableMap()
        val changes = PlaidTransactionResult(listOf(addition), emptyList(), emptyList())
        val existing = listOf(TransactionRead("transactions", "existing", FireflyFixtures.getTransaction(), ObjectLink()))
        val creates = listOf(FireflyTransactionDto(null, FireflyFixtures.getTransaction().transactions.single()))
        val converted = TransactionConverter.ConvertPollSyncResult(creates, emptyList())
        stagePlaidChanges(changes)
        whenever(fireflyTransactionService.fetchExistingFireflyTransactions()).thenReturn(existing)
        whenever(converter.convertPollSync(accountMap, changes.created, existing)).thenReturn(converted)

        orchestrator.processTransactions(accountMap, accountAccessTokenSequence, cursorMap)

        inOrder(fireflyTransactionService, plaidSyncService, converter, correctionQueue, replacementJournal, cursorManager) {
            verify(fireflyTransactionService).recoverTransferReplacements(emptyList())
            verify(plaidSyncService).processPlaidTransactions(accountAccessTokenSequence, nextCursors.toMutableMap(), false)
            verify(fireflyTransactionService).fetchExistingFireflyTransactions()
            verify(converter).convertPollSync(accountMap, changes.created, existing)
            verify(fireflyTransactionService).processFireflyCreatesAndTransfers(creates, emptyList(), existing)
            verify(correctionQueue).process(eq(accountMap), eq(changes), eq(cursorMap), eq(nextCursors), any())
            verify(replacementJournal).prepareCursorCommit(nextCursors)
            verify(cursorManager).writeCursorMap(nextCursors)
            verify(correctionQueue).finishCursorCommit(nextCursors)
        }
        assertEquals(nextCursors, cursorMap)
    }

    @ParameterizedTest(name = "poll has corrections: {0}")
    @ValueSource(booleans = [false, true])
    fun `skips the full scan and still processes corrections and commits cursors`(hasCorrections: Boolean) = runBlocking {
        val cursorMap = initialCursors.toMutableMap()
        val changes = PlaidTransactionResult(
            created = emptyList(),
            updated = emptyList(),
            deleted = if (hasCorrections) listOf(RemovedTransaction("removed1", "account1")) else emptyList(),
        )
        stagePlaidChanges(changes)

        orchestrator.processTransactions(accountMap, accountAccessTokenSequence, cursorMap)

        verify(fireflyTransactionService, never()).fetchExistingFireflyTransactions()
        verifyNoInteractions(converter)
        inOrder(correctionQueue, cursorManager) {
            verify(correctionQueue).process(eq(accountMap), eq(changes), eq(cursorMap), eq(nextCursors), any())
            verify(cursorManager).writeCursorMap(nextCursors)
            verify(correctionQueue).finishCursorCommit(nextCursors)
        }
        assertEquals(nextCursors, cursorMap)
    }

    @Test
    fun `a failed Firefly scan leaves the staged Plaid cursor uncommitted`() = runBlocking {
        val cursorMap = initialCursors.toMutableMap()
        val failure = IllegalStateException("Invalid Firefly pagination")
        stagePlaidChanges(PlaidTransactionResult(listOf(addition), emptyList(), emptyList()))
        whenever(fireflyTransactionService.fetchExistingFireflyTransactions()).thenThrow(failure)

        assertSame(failure, assertFailsWith<IllegalStateException> {
            orchestrator.processTransactions(accountMap, accountAccessTokenSequence, cursorMap)
        })

        assertEquals(initialCursors, cursorMap)
        verify(replacementJournal, never()).prepareCursorCommit(any())
        verifyNoInteractions(converter, correctionQueue, cursorManager)
    }

    private suspend fun stagePlaidChanges(changes: PlaidTransactionResult) {
        whenever(plaidSyncService.processPlaidTransactions(eq(accountAccessTokenSequence), any(), eq(false))).thenAnswer {
            val stagedCursors = it.getArgument<MutableMap<PlaidAccessToken, PlaidSyncCursor>>(1)
            assertEquals(initialCursors, stagedCursors)
            stagedCursors.putAll(nextCursors)
            changes
        }
    }
}
