package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    /**
     * Test that the orchestrator correctly initializes cursors.
     * 
     * This test verifies that the orchestrator reads the cursor map, gets account mappings,
     * and initializes cursors for access tokens that don't have one yet.
     */
    @Test
    fun testInitializeCursors() = runBlocking {
        // Setup
        val cursorMap = mutableMapOf<String, String>() // PlaidAccessToken to PlaidSyncCursor
        val accountMap = mapOf<String, Int>("account1" to 1) // PlaidAccountId to FireflyAccountId
        val accountAccessTokenSequence = sequenceOf(Pair("token1", listOf("account1")))

        whenever(cursorManager.readCursorMap()).thenReturn(cursorMap)
        whenever(syncHelper.getAllPlaidAccessTokenAccountIdSets()).thenReturn(Pair(accountMap, accountAccessTokenSequence))

        // Create an orchestrator instance
        val orchestrator = PolledSyncOrchestrator(
            30, // syncFrequencyMinutes
            syncHelper,
            cursorManager,
            plaidSyncService,
            fireflyTransactionService,
            converter
        )

        // Call the method we want to test
        orchestrator.initializeCursors()

        // Verify interactions
        verify(cursorManager).readCursorMap()
        verify(syncHelper).getAllPlaidAccessTokenAccountIdSets()
        verify(plaidSyncService).initializeCursors(eq(accountAccessTokenSequence), eq(cursorMap))
        verify(cursorManager).writeCursorMap(eq(cursorMap))
    }

    /**
     * Test that the orchestrator correctly processes transactions.
     * 
     * This test verifies that the orchestrator fetches existing Firefly transactions,
     * processes Plaid transactions, converts them to Firefly format, and updates Firefly.
     */
    @Test
    fun testProcessTransactions() = runBlocking {
        // Setup
        val cursorMap = mutableMapOf<String, String>() // PlaidAccessToken to PlaidSyncCursor
        val accountMap = mapOf<String, Int>("account1" to 1) // PlaidAccountId to FireflyAccountId
        val accountAccessTokenSequence = sequenceOf(Pair("token1", listOf("account1")))
        val existingFireflyTxs = listOf<TransactionRead>()

        val plaidTransactionResult = PlaidTransactionResult(
            created = emptyList<PlaidTransaction>(),
            updated = emptyList<PlaidTransaction>(),
            deleted = emptyList<String>()
        )

        val convertResult = TransactionConverter.ConvertPollSyncResult(
            creates = emptyList<FireflyTransactionDto>(),
            updates = emptyList<FireflyTransactionDto>(),
            deletes = emptyList<String>()
        )

        whenever(cursorManager.readCursorMap()).thenReturn(cursorMap)
        whenever(syncHelper.getAllPlaidAccessTokenAccountIdSets()).thenReturn(Pair(accountMap, accountAccessTokenSequence))
        whenever(fireflyTransactionService.fetchExistingFireflyTransactions()).thenReturn(existingFireflyTxs)
        whenever(plaidSyncService.processPlaidTransactions(eq(accountAccessTokenSequence), eq(cursorMap))).thenReturn(plaidTransactionResult)
        whenever(converter.convertPollSync(
            eq(accountMap),
            eq(plaidTransactionResult.created),
            eq(plaidTransactionResult.updated),
            eq(plaidTransactionResult.deleted),
            eq(existingFireflyTxs)
        )).thenReturn(convertResult)

        // Create an orchestrator instance
        val orchestrator = PolledSyncOrchestrator(
            30, // syncFrequencyMinutes
            syncHelper,
            cursorManager,
            plaidSyncService,
            fireflyTransactionService,
            converter
        )

        // Call the method we want to test
        orchestrator.processTransactions(accountMap, accountAccessTokenSequence, cursorMap)

        // Verify interactions
        verify(fireflyTransactionService).fetchExistingFireflyTransactions()
        verify(plaidSyncService).processPlaidTransactions(eq(accountAccessTokenSequence), eq(cursorMap))
        verify(converter).convertPollSync(
            eq(accountMap),
            eq(plaidTransactionResult.created),
            eq(plaidTransactionResult.updated),
            eq(plaidTransactionResult.deleted),
            eq(existingFireflyTxs)
        )
        verify(fireflyTransactionService).processFireflyTransactionUpdates(
            eq(convertResult.creates),
            eq(convertResult.updates),
            eq(convertResult.deletes)
        )
        verify(cursorManager).writeCursorMap(eq(cursorMap))
    }
}
