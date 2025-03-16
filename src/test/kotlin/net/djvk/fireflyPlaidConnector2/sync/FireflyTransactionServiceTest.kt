package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate

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
            val creates = listOf<FireflyTransactionDto>(mock())
            val updates = listOf<FireflyTransactionDto>(mock())
            val deletes = listOf("1", "2")

            // Mock update transaction with ID
            whenever(updates[0].id).thenReturn("update-id")

            // Execute
            fireflyTransactionService.processFireflyTransactionUpdates(creates, updates, deletes)

            // Verify
            verify(syncHelper).optimisticInsertBatchIntoFirefly(eq(creates))
            verify(syncHelper).deleteBatchInFirefly(eq(listOf("update-id")))
            verify(syncHelper).pessimisticInsertBatchIntoFirefly(eq(updates))
            verify(syncHelper).deleteBatchInFirefly(eq(deletes))
        }
    }

    @Test
    fun testProcessFireflyTransactionUpdatesHandlesDeleteFailure() {
        runBlocking {
            // Setup
            val update = mock<FireflyTransactionDto>()
            whenever(update.id).thenReturn("update-id")

            // Mock delete to throw exception
            whenever(syncHelper.deleteBatchInFirefly(eq(listOf("update-id")))).thenThrow(RuntimeException("Delete failed"))

            // Execute
            fireflyTransactionService.processFireflyTransactionUpdates(
                emptyList(),
                listOf(update),
                emptyList()
            )

            // Verify
            verify(syncHelper).deleteBatchInFirefly(eq(listOf("update-id")))
            // Verify that pessimisticInsertBatchIntoFirefly is not called due to delete failure
            verify(syncHelper, never()).pessimisticInsertBatchIntoFirefly(any())
        }
    }

    @Test
    fun testFetchExistingFireflyTransactions() {
        runBlocking {
            // Setup - simplified approach
            val transactions = listOf<TransactionRead>(mock(), mock())

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
