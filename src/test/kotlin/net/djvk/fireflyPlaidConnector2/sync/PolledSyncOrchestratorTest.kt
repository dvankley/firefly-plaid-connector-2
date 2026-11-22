package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.stream.Stream

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
            converter,
            replacementJournal,
            correctionQueue,
        )

        // Call the method we want to test
        orchestrator.initializeCursors()

        // Verify interactions
        verify(cursorManager).readCursorMap()
        verify(syncHelper).getAllPlaidAccessTokenAccountIdSets()
        verify(plaidSyncService).initializeCursors(eq(accountAccessTokenSequence), eq(cursorMap))
        verify(cursorManager).writeCursorMap(eq(cursorMap))
    }

    companion object {
        @JvmStatic
        fun provideProcessTransactionsTestCases(): Stream<Arguments> {
            // Standard account mapping for all test cases
            val accountMap = mapOf(
                "account1" to 1,
                "account2" to 2
            )

            // Test case 1: Empty lists (baseline test)
            val emptyPlaidResult = PlaidTransactionResult(
                created = emptyList(),
                updated = emptyList(),
                deleted = emptyList()
            )
            val emptyConvertResult = TransactionConverter.ConvertPollSyncResult(
                creates = emptyList(),
                updates = emptyList(),
            )

            // Test case 2: Created transactions only
            val createdPlaidTx = PlaidFixtures.getPaymentTransaction(
                name = "Created Transaction",
                accountId = "account1",
                amount = 100.0,
                transactionId = "created1"
            )
            val createdPlaidResult = PlaidTransactionResult(
                created = listOf(createdPlaidTx),
                updated = emptyList(),
                deleted = emptyList()
            )
            val createdFireflyTxDto = FireflyTransactionDto(
                id = null,
                tx = FireflyFixtures.getTransaction(
                    description = "Created Transaction",
                    amount = "100.0",
                    sourceId = "1"
                ).transactions.first()
            )
            val createdConvertResult = TransactionConverter.ConvertPollSyncResult(
                creates = listOf(createdFireflyTxDto),
                updates = emptyList(),
            )

            return Stream.of(
                Arguments.of(
                    "Empty lists (baseline test)",
                    accountMap,
                    emptyList<TransactionRead>(),
                    emptyPlaidResult,
                    emptyConvertResult
                ),
                Arguments.of(
                    "Created transactions only",
                    accountMap,
                    emptyList<TransactionRead>(),
                    createdPlaidResult,
                    createdConvertResult
                ),
            )
        }
    }

    /**
     * Test that the orchestrator correctly processes transactions.
     * 
     * This test verifies that the orchestrator fetches existing Firefly transactions,
     * processes Plaid transactions, converts them to Firefly format, and updates Firefly.
     * It uses parameterized testing to test multiple scenarios with different input data.
     */
    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideProcessTransactionsTestCases")
    fun testProcessTransactions(
        testName: String,
        accountMap: Map<String, Int>,
        existingFireflyTxs: List<TransactionRead>,
        plaidTransactionResult: PlaidTransactionResult,
        convertResult: TransactionConverter.ConvertPollSyncResult
    ) = runBlocking {
        // Setup
        val cursorMap = mutableMapOf<String, String>() // PlaidAccessToken to PlaidSyncCursor
        val accountAccessTokenSequence = sequenceOf(Pair("token1", listOf("account1")))

        whenever(cursorManager.readCursorMap()).thenReturn(cursorMap)
        whenever(syncHelper.getAllPlaidAccessTokenAccountIdSets()).thenReturn(Pair(accountMap, accountAccessTokenSequence))
        whenever(fireflyTransactionService.fetchExistingFireflyTransactions()).thenReturn(existingFireflyTxs)
        whenever(plaidSyncService.processPlaidTransactions(eq(accountAccessTokenSequence), eq(cursorMap), eq(false)))
            .thenReturn(plaidTransactionResult)
        whenever(converter.convertPollSync(
            eq(accountMap),
            eq(plaidTransactionResult.created),
            eq(existingFireflyTxs)
        )).thenReturn(convertResult)

        // Create an orchestrator instance
        val orchestrator = PolledSyncOrchestrator(
            30, // syncFrequencyMinutes
            syncHelper,
            cursorManager,
            plaidSyncService,
            fireflyTransactionService,
            converter,
            replacementJournal,
            correctionQueue,
        )

        // Call the method we want to test
        orchestrator.processTransactions(accountMap, accountAccessTokenSequence, cursorMap)

        // Verify interactions
        verify(fireflyTransactionService).fetchExistingFireflyTransactions()
        verify(plaidSyncService).processPlaidTransactions(eq(accountAccessTokenSequence), eq(cursorMap), eq(false))
        verify(converter).convertPollSync(
            eq(accountMap),
            eq(plaidTransactionResult.created),
            eq(existingFireflyTxs)
        )
        verify(fireflyTransactionService).processFireflyCreatesAndTransfers(
            eq(convertResult.creates),
            eq(convertResult.updates),
            eq(existingFireflyTxs),
        )
        inOrder(correctionQueue, cursorManager) {
            verify(correctionQueue).process(eq(accountMap), eq(plaidTransactionResult), eq(cursorMap), eq(cursorMap), any())
            verify(cursorManager).writeCursorMap(eq(cursorMap))
            verify(correctionQueue).finishCursorCommit(eq(cursorMap))
        }
    }
}
