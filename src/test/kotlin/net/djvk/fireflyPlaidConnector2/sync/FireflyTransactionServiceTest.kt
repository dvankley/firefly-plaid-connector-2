package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.net.URI
import kotlin.test.assertFailsWith

class FireflyTransactionServiceTest {

    private val fireflyTxApi: TransactionsApi = mock()
    private val syncHelper: SyncHelper = mock()
    private val replacementJournal: TransferReplacementJournal = mock()
    private val existingFireflyPullWindowDays = 30

    private val fireflyTransactionService = FireflyTransactionService(
        fireflyTxApi,
        syncHelper,
        FireflyApiWrapper(RetryProperties(maxAttempts = 1)),
        replacementJournal,
        existingFireflyPullWindowDays
    )

    @BeforeEach
    fun stubSearch() {
        runBlocking {
            whenever(syncHelper.findTransactionsByExternalId(any())).thenReturn(emptyList())
        }
    }

    @Test
    fun retriesDnsFailureWithoutAdvancingPage() = runBlocking {
        val failure = java.nio.channels.UnresolvedAddressException()
        whenever(fireflyTxApi.listTransaction(eq(0), any(), any(), any())).thenThrow(failure)
        val service = FireflyTransactionService(
            fireflyTxApi, syncHelper,
            FireflyApiWrapper(RetryProperties(maxAttempts = 3, initialDelayMs = 0)),
            replacementJournal,
            existingFireflyPullWindowDays,
        )
        val thrown = kotlin.test.assertFailsWith<java.nio.channels.UnresolvedAddressException> {
            service.fetchExistingFireflyTransactions()
        }
        kotlin.test.assertSame(failure, thrown)
        verify(fireflyTxApi, times(3)).listTransaction(eq(0), any(), any(), any())
    }

    @Test
    fun testProcessFireflyTransactionUpdates() {
        runBlocking {
            // Setup
            val createTransaction = FireflyFixtures.getTransaction()
            val createTxSplit = createTransaction.transactions.first()
            val creates = listOf(FireflyTransactionDto(null, createTxSplit))

            // Create a transfer update
            val transferTransaction = FireflyFixtures.getTransaction(
                type = TransactionTypeProperty.transfer, sourceId = "1", destinationId = "2", externalId = "plaid-new",
            )
            val transferTxSplit = transferTransaction.transactions.first()
            val transferUpdate = FireflyTransactionDto("transfer-update-id", transferTxSplit)

            // Create a non-transfer update
            val nonTransferTransaction = FireflyFixtures.getTransaction(type = TransactionTypeProperty.withdrawal)
            val nonTransferTxSplit = nonTransferTransaction.transactions.first()
            val nonTransferUpdate = FireflyTransactionDto("non-transfer-update-id", nonTransferTxSplit)

            val updates = listOf(transferUpdate, nonTransferUpdate)
            val deletes = listOf("1", "2")

            // Execute
            fireflyTransactionService.processFireflyTransactionUpdates(creates, updates, deletes, listOf(originalTransferLeg()))

            // Verify
            verify(syncHelper).optimisticInsertBatchIntoFirefly(eq(creates))

            // Verify transfer update is handled with delete and create
            inOrder(replacementJournal, syncHelper) {
                verify(replacementJournal).append(any())
                verify(syncHelper).deleteBatchInFirefly(eq(listOf("transfer-update-id")))
                verify(syncHelper).insertTransferReplacement(eq(transferUpdate.toTransactionStore()))
                verify(replacementJournal).complete("transfer-update-id")
            }

            // Verify non-transfer update is handled with direct update
            verify(syncHelper).updateBatchInFirefly(eq(listOf(nonTransferUpdate)))

            // Verify deletes
            verify(syncHelper).deleteBatchInFirefly(eq(deletes))
        }
    }

    @Test
    fun testProcessFireflyTransactionUpdatesHandlesDeleteFailure() {
        runBlocking {
            // Setup a transfer update
            val transferTransaction = FireflyFixtures.getTransaction(
                type = TransactionTypeProperty.transfer, sourceId = "1", destinationId = "2", externalId = "plaid-new",
            )
            val transferTxSplit = transferTransaction.transactions.first()
            val transferUpdate = FireflyTransactionDto("transfer-update-id", transferTxSplit)

            // Mock delete to throw exception
            whenever(syncHelper.deleteBatchInFirefly(eq(listOf("transfer-update-id"))))
                .thenThrow(IllegalStateException("Delete failed"))

            // Execute
            assertFailsWith<IllegalStateException> {
                fireflyTransactionService.processFireflyTransactionUpdates(
                    emptyList(), listOf(transferUpdate), emptyList(), listOf(originalTransferLeg())
                )
            }

            // Verify
            verify(syncHelper).deleteBatchInFirefly(eq(listOf("transfer-update-id")))
            verify(syncHelper, never()).insertTransferReplacement(any())
            verify(replacementJournal, never()).complete(any())
        }
    }

    @Test
    fun testProcessFireflyNonTransferUpdates() {
        runBlocking {
            // Setup non-transfer updates
            val nonTransferTransaction = FireflyFixtures.getTransaction(type = TransactionTypeProperty.withdrawal)
            val nonTransferTxSplit = nonTransferTransaction.transactions.first()
            val nonTransferUpdate = FireflyTransactionDto("non-transfer-update-id", nonTransferTxSplit)

            // Execute
            fireflyTransactionService.processFireflyTransactionUpdates(
                emptyList(),
                listOf(nonTransferUpdate),
                emptyList(),
                emptyList(),
            )

            // Verify that updateBatchInFirefly is called with the non-transfer update
            verify(syncHelper).updateBatchInFirefly(eq(listOf(nonTransferUpdate)))
            verify(syncHelper, never()).deleteBatchInFirefly(eq(listOf("non-transfer-update-id")))
            verify(syncHelper, never()).insertTransferReplacement(any())
        }
    }

    @Test
    fun testFetchExistingFireflyTransactions() {
        runBlocking {
            // Setup - simplified approach
            val transaction1 = FireflyFixtures.getTransaction(description = "Test Transaction 1")
            val transaction2 = FireflyFixtures.getTransaction(description = "Test Transaction 2")
            val transactions = listOf(
                TransactionRead(
                    type = "transactions",
                    id = "1",
                    attributes = transaction1,
                    links = ObjectLink(self = URI("http://example.com/transactions/1"))
                ),
                TransactionRead(
                    type = "transactions",
                    id = "2",
                    attributes = transaction2,
                    links = ObjectLink(self = URI("http://example.com/transactions/2"))
                )
            )

            // Create mocks outside the coroutine
            val mockResponse = mock<net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.HttpResponse<net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionArray>>()
            val mockArray = mock<net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionArray>()
            val mockMeta = mock<net.djvk.fireflyPlaidConnector2.api.firefly.models.Meta>()

            // Setup basic mocks
            whenever(mockArray.data).thenReturn(transactions)
            whenever(mockArray.meta).thenReturn(mockMeta)
            whenever(mockMeta.pagination).thenReturn(null) // No pagination = single page
            whenever(mockResponse.body()).thenReturn(mockArray)

            // Mock API call
            whenever(fireflyTxApi.listTransaction(any(), any(), any(), any())).thenReturn(mockResponse)

            // Execute
            val result = fireflyTransactionService.fetchExistingFireflyTransactions()

            // Verify
            assertEquals(transactions, result)
            verify(fireflyTxApi).listTransaction(
                eq(0),
                any(),
                any(),
                any()
            )
        }
    }

    private fun originalTransferLeg() = TransactionRead(
        type = "transactions",
        id = "transfer-update-id",
        attributes = FireflyFixtures.getTransaction(sourceId = "1", externalId = "plaid-old"),
        links = ObjectLink(self = URI("https://firefly.test/api/v1/transactions/transfer-update-id")),
    )
}
