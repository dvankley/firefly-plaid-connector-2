package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
