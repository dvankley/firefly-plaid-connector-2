package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.defaultOffsetNow
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.transactions.PersonalFinanceCategoryEnum
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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

    companion object {
        /**
         * Provides test cases for the processTransactions test.
         * Each test case includes different combinations of:
         * - Created Plaid transactions
         * - Updated Plaid transactions
         * - Deleted Plaid transaction IDs
         * - Existing Firefly transactions
         * - Expected FireflyTransactionDto creates
         * - Expected FireflyTransactionDto updates
         * - Expected FireflyTransactionId deletes
         */
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
                deletes = emptyList()
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
                deletes = emptyList()
            )

            // Test case 3: Updated transactions only
            val updatedPlaidTx = PlaidFixtures.getPaymentTransaction(
                name = "Updated Transaction",
                accountId = "account1",
                amount = 200.0,
                transactionId = "updated1"
            )
            val existingFireflyTx = FireflyFixtures.getTransaction(
                description = "Existing Transaction",
                amount = "150.0",
                sourceId = "1"
            )
            val updatedPlaidResult = PlaidTransactionResult(
                created = emptyList(),
                updated = listOf(updatedPlaidTx),
                deleted = emptyList()
            )
            val updatedFireflyTxDto = FireflyTransactionDto(
                id = "1", // Assuming this is the ID of the existing transaction
                tx = FireflyFixtures.getTransaction(
                    description = "Updated Transaction",
                    amount = "200.0",
                    sourceId = "1"
                ).transactions.first()
            )
            val updatedConvertResult = TransactionConverter.ConvertPollSyncResult(
                creates = emptyList(),
                updates = listOf(updatedFireflyTxDto),
                deletes = emptyList()
            )

            // Test case 4: Deleted transactions only
            val deletedPlaidResult = PlaidTransactionResult(
                created = emptyList(),
                updated = emptyList(),
                deleted = listOf("deleted1")
            )
            val deletedConvertResult = TransactionConverter.ConvertPollSyncResult(
                creates = emptyList(),
                updates = emptyList(),
                deletes = listOf("1") // Assuming this is the ID of the transaction to delete
            )

            // Test case 5: Combination of created, updated, and deleted transactions
            val combinedPlaidResult = PlaidTransactionResult(
                created = listOf(createdPlaidTx),
                updated = listOf(updatedPlaidTx),
                deleted = listOf("deleted1")
            )
            val combinedConvertResult = TransactionConverter.ConvertPollSyncResult(
                creates = listOf(createdFireflyTxDto),
                updates = listOf(updatedFireflyTxDto),
                deletes = listOf("1")
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
                Arguments.of(
                    "Updated transactions only",
                    accountMap,
                    listOf(existingFireflyTx),
                    updatedPlaidResult,
                    updatedConvertResult
                ),
                Arguments.of(
                    "Deleted transactions only",
                    accountMap,
                    listOf(existingFireflyTx),
                    deletedPlaidResult,
                    deletedConvertResult
                ),
                Arguments.of(
                    "Combination of created, updated, and deleted transactions",
                    accountMap,
                    listOf(existingFireflyTx),
                    combinedPlaidResult,
                    combinedConvertResult
                )
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
