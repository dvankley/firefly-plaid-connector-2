package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime

class FireflyTransactionServiceTest {

    private val fireflyTxApi: TransactionsApi = mock()
    private val syncHelper: SyncHelper = mock()
    private val existingFireflyPullWindowDays = 30

    private val fireflyTransactionService = FireflyTransactionService(
        fireflyTxApi,
        syncHelper,
        existingFireflyPullWindowDays
    )

    @Test
    fun testProcessFireflyTransactionUpdates() {
        runBlocking {
            // Setup
            val createTransaction = FireflyFixtures.getTransaction()
            val createTxSplit = createTransaction.transactions.first()
            val creates = listOf(FireflyTransactionDto(null, createTxSplit))

            // Create a transfer update
            val transferTransaction = FireflyFixtures.getTransaction(type = TransactionTypeProperty.transfer)
            val transferTxSplit = transferTransaction.transactions.first()
            val transferUpdate = FireflyTransactionDto("transfer-update-id", transferTxSplit)

            // Create a non-transfer update
            val nonTransferTransaction = FireflyFixtures.getTransaction(type = TransactionTypeProperty.withdrawal)
            val nonTransferTxSplit = nonTransferTransaction.transactions.first()
            val nonTransferUpdate = FireflyTransactionDto("non-transfer-update-id", nonTransferTxSplit)

            val updates = listOf(transferUpdate, nonTransferUpdate)
            val deletes = listOf("1", "2")

            // Execute
            fireflyTransactionService.processFireflyTransactionUpdates(creates, updates, deletes)

            // Verify
            verify(syncHelper).optimisticInsertBatchIntoFirefly(eq(creates))

            // Verify transfer update is handled with delete and create
            verify(syncHelper).deleteBatchInFirefly(eq(listOf("transfer-update-id")))
            verify(syncHelper).pessimisticInsertBatchIntoFirefly(eq(listOf(transferUpdate)))

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
            val transferTransaction = FireflyFixtures.getTransaction(type = TransactionTypeProperty.transfer)
            val transferTxSplit = transferTransaction.transactions.first()
            val transferUpdate = FireflyTransactionDto("transfer-update-id", transferTxSplit)

            // Mock delete to throw exception
            whenever(syncHelper.deleteBatchInFirefly(eq(listOf("transfer-update-id")))).thenThrow(RuntimeException("Delete failed"))

            // Execute
            fireflyTransactionService.processFireflyTransactionUpdates(
                emptyList(),
                listOf(transferUpdate),
                emptyList()
            )

            // Verify
            verify(syncHelper).deleteBatchInFirefly(eq(listOf("transfer-update-id")))
            // Verify that pessimisticInsertBatchIntoFirefly is not called due to delete failure
            verify(syncHelper, never()).pessimisticInsertBatchIntoFirefly(any())
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
                emptyList()
            )

            // Verify that updateBatchInFirefly is called with the non-transfer update
            verify(syncHelper).updateBatchInFirefly(eq(listOf(nonTransferUpdate)))
            // Verify that deleteBatchInFirefly and pessimisticInsertBatchIntoFirefly are not called
            verify(syncHelper, never()).deleteBatchInFirefly(eq(listOf("non-transfer-update-id")))
            verify(syncHelper, never()).pessimisticInsertBatchIntoFirefly(any())
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
}
