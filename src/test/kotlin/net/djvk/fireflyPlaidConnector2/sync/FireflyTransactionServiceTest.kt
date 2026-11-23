package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.HttpResponse
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Meta
import net.djvk.fireflyPlaidConnector2.api.firefly.models.MetaPagination
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.PageLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionArray
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeFilter
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.net.URI
import java.time.LocalDate
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
        whenever(fireflyTxApi.listTransaction(eq(1), any(), any(), any())).thenThrow(failure)
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
        verify(fireflyTxApi, times(3)).listTransaction(eq(1), any(), any(), any())
        verifyNoMoreInteractions(fireflyTxApi)
    }

    @Test
    fun testProcessFireflyCreatesAndTransfers() {
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

            fireflyTransactionService.processFireflyCreatesAndTransfers(
                creates, listOf(transferUpdate), listOf(originalTransferLeg())
            )

            // Verify
            verify(syncHelper).optimisticInsertBatchIntoFirefly(eq(creates))

            // Verify transfer update is handled with delete and create
            inOrder(replacementJournal, syncHelper) {
                verify(replacementJournal).append(any())
                verify(syncHelper).deleteBatchInFirefly(eq(listOf("transfer-update-id")))
                verify(syncHelper).insertTransferReplacement(eq(transferUpdate.toTransactionStore()))
                verify(replacementJournal).complete("transfer-update-id")
            }

        }
    }

    @Test
    fun testProcessFireflyCreatesAndTransfersHandlesDeleteFailure() {
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
                fireflyTransactionService.processFireflyCreatesAndTransfers(
                    emptyList(), listOf(transferUpdate), listOf(originalTransferLeg())
                )
            }

            // Verify
            verify(syncHelper).deleteBatchInFirefly(eq(listOf("transfer-update-id")))
            verify(syncHelper, never()).insertTransferReplacement(any())
            verify(replacementJournal, never()).complete(any())
        }
    }

    @Test
    fun `a first response without pagination is a single page`() = runBlocking {
        val transactions = listOf(transaction("1"), transaction("2"))
        stubPage(1, transactions, pagination = null)

        assertEquals(transactions, fireflyTransactionService.fetchExistingFireflyTransactions())

        verify(fireflyTxApi).listTransaction(eq(1), any(), any(), any())
        verifyNoMoreInteractions(fireflyTxApi)
    }

    @ParameterizedTest
    @ValueSource(ints = [20, 25])
    fun `fetches every page at or beyond the old request limit`(totalPages: Int) = runBlocking {
        val transactions = (1..totalPages).map { page -> transaction(page.toString()) }
        for ((index, transaction) in transactions.withIndex()) {
            val page = index + 1
            stubPage(page, listOf(transaction), MetaPagination(currentPage = page, totalPages = totalPages))
        }

        assertEquals(transactions, fireflyTransactionService.fetchExistingFireflyTransactions())

        val pages = argumentCaptor<Int>()
        val starts = argumentCaptor<LocalDate>()
        val ends = argumentCaptor<LocalDate>()
        verify(fireflyTxApi, times(totalPages)).listTransaction(
            pages.capture(), starts.capture(), ends.capture(), eq(TransactionTypeFilter.all),
        )
        assertEquals((1..totalPages).toList(), pages.allValues)
        val end = ends.allValues.distinct().single()
        assertEquals(end.minusDays(existingFireflyPullWindowDays.toLong()), starts.allValues.distinct().single())
        verifyNoMoreInteractions(fireflyTxApi)
    }

    @Test
    fun `uses the last page reported by each response`(): Unit = runBlocking {
        stubPage(1, emptyList(), MetaPagination(currentPage = 1, totalPages = 2))
        stubPage(2, emptyList(), MetaPagination(currentPage = 2, totalPages = 3))
        val lastTransaction = transaction("3")
        stubPage(3, listOf(lastTransaction), MetaPagination(currentPage = 3, totalPages = 3))

        assertEquals(listOf(lastTransaction), fireflyTransactionService.fetchExistingFireflyTransactions())
        verify(fireflyTxApi, times(3)).listTransaction(any(), any(), any(), any())
    }

    @ParameterizedTest(name = "page 2 returns page {0} of {1}")
    @CsvSource("1, 1", "3, 3", "2, 1")
    fun `rejects inconsistent pagination before accepting a final page`(currentPage: Int, totalPages: Int): Unit = runBlocking {
        stubPage(1, listOf(transaction("1")), MetaPagination(currentPage = 1, totalPages = 3))
        stubPage(2, listOf(transaction("2")), MetaPagination(currentPage = currentPage, totalPages = totalPages))

        val failure = assertFailsWith<IllegalStateException> {
            fireflyTransactionService.fetchExistingFireflyTransactions()
        }
        assertEquals("Invalid Firefly pagination for requested page 2: received page $currentPage of $totalPages", failure.message)
        verify(fireflyTxApi, times(2)).listTransaction(any(), any(), any(), any())
    }

    @Test
    fun `rejects missing pagination after the first page`(): Unit = runBlocking {
        stubPage(1, listOf(transaction("1")), MetaPagination(currentPage = 1, totalPages = 3))
        stubPage(2, listOf(transaction("2")), pagination = null)

        val failure = assertFailsWith<IllegalStateException> {
            fireflyTransactionService.fetchExistingFireflyTransactions()
        }
        assertEquals("Missing Firefly pagination metadata for requested page 2", failure.message)
        verify(fireflyTxApi, times(2)).listTransaction(any(), any(), any(), any())
    }

    private suspend fun stubPage(page: Int, transactions: List<TransactionRead>, pagination: MetaPagination?) {
        val response = mock<HttpResponse<TransactionArray>>()
        whenever(response.body()).thenReturn(TransactionArray(transactions, Meta(pagination), PageLink()))
        whenever(fireflyTxApi.listTransaction(eq(page), any(), any(), any())).thenReturn(response)
    }

    private fun transaction(id: String) = TransactionRead(
        type = "transactions",
        id = id,
        attributes = FireflyFixtures.getTransaction(),
        links = ObjectLink(self = URI("https://firefly.test/api/v1/transactions/$id")),
    )

    private fun originalTransferLeg() = TransactionRead(
        type = "transactions",
        id = "transfer-update-id",
        attributes = FireflyFixtures.getTransaction(sourceId = "1", externalId = "plaid-old"),
        links = ObjectLink(self = URI("https://firefly.test/api/v1/transactions/transfer-update-id")),
    )
}
