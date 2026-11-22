package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper
import net.djvk.fireflyPlaidConnector2.api.plaid.models.RemovedTransaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequestOptions
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncResponse
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsUpdateStatus
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidMock
import net.djvk.fireflyPlaidConnector2.lib.createPlaidResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.stream.Stream
import kotlin.test.assertFailsWith

/**
 * Tests for the PlaidSyncService class.
 */
class PlaidSyncServiceTest {
    @Test
    fun `initial backfill respects both date boundaries while saved cursors still get incremental updates`() = runBlocking {
        val cutoff = LocalDate.of(2025, 1, 10)
        val through = cutoff.plusDays(4)
        val plaid = PlaidMock()
        val service = PlaidSyncService(plaid.wrapper, 100, false, initialBackfillAfter = cutoff, initialBackfillThrough = through)
        val accounts = sequenceOf("fresh-item" to listOf("mapped"), "saved-item" to listOf("mapped"))
        val cursors = mutableMapOf("saved-item" to "saved-cursor")

        service.initializeCursors(accounts, cursors)
        verifyNoInteractions(plaid.api)

        val boundary = PlaidFixtures.getPaymentTransaction(accountId = "mapped", transactionId = "boundary", date = cutoff)
        val recent = boundary.copy(transactionId = "recent", date = cutoff.plusDays(1))
        val lastDay = recent.copy(transactionId = "last-day", date = through)
        val future = recent.copy(transactionId = "future", date = through.plusDays(1))
        val oldUpdate = boundary.copy(transactionId = "old-update", date = cutoff.minusDays(1))
        fun response(nextCursor: String, added: List<Transaction>, modified: List<Transaction> = emptyList(), more: Boolean = false) =
            createPlaidResponse(TransactionsSyncResponse(
                transactionsUpdateStatus = TransactionsUpdateStatus.HISTORICAL_UPDATE_COMPLETE,
                accounts = emptyList(), added = added, modified = modified, removed = emptyList(),
                nextCursor = nextCursor, hasMore = more, requestId = "request",
            ))

        val firstPage = response("page-two", listOf(boundary, recent), more = true)
        val secondPage = response("final-cursor", listOf(lastDay, future, recent.copy(accountId = "unmapped")))
        val savedPage = response("next-saved-cursor", emptyList(), modified = listOf(oldUpdate))
        whenever(plaid.api.transactionsSync(service.getTransactionSyncRequest("fresh-item", null))).thenReturn(firstPage)
        whenever(plaid.api.transactionsSync(service.getTransactionSyncRequest("fresh-item", "page-two"))).thenReturn(secondPage)
        whenever(plaid.api.transactionsSync(service.getTransactionSyncRequest("saved-item", "saved-cursor"))).thenReturn(savedPage)

        val result = service.processPlaidTransactions(accounts, cursors)

        assertEquals(listOf(recent, lastDay), result.created)
        assertEquals(listOf(oldUpdate), result.updated)
        assertEquals(mapOf("fresh-item" to "final-cursor", "saved-item" to "next-saved-cursor"), cursors)
    }

    @Test
    fun removedTransactionsAreFilteredByConfiguredAccountAcrossPages() = runBlocking {
        val wrapper: PlaidApiWrapper = mock()
        val service = spy(PlaidSyncService(wrapper, 100, false))
        fun page(cursor: String, hasMore: Boolean, vararg removed: RemovedTransaction): TransactionsSyncResponse {
            val response: TransactionsSyncResponse = mock()
            whenever(response.added).thenReturn(emptyList())
            whenever(response.modified).thenReturn(emptyList())
            whenever(response.removed).thenReturn(removed.toList())
            whenever(response.nextCursor).thenReturn(cursor)
            whenever(response.hasMore).thenReturn(hasMore)
            return response
        }
        val first = page("page2", true,
            RemovedTransaction("included1", "configured"),
            RemovedTransaction("excluded1", "other"))
        val second = page("done", false,
            RemovedTransaction("excluded2", "other"),
            RemovedTransaction("included2", "configured"))
        doReturn(first).whenever(service).executeTransactionSyncRequest("token", "saved", 100)
        doReturn(second).whenever(service).executeTransactionSyncRequest("token", "page2", 100)
        val cursors = mutableMapOf("token" to "saved")
        val result = service.processPlaidTransactions(sequenceOf("token" to listOf("configured")), cursors)
        assertEquals(listOf("included1", "included2"), result.deleted)
        assertEquals("done", cursors["token"])
    }

    @Test
    fun recoveryRequiresPlaidItemsToSucceedEvenWithAllowItemToFail() = runBlocking {
        val service = spy(PlaidSyncService(mock(), 100, true))
        doReturn(null).whenever(service).executeTransactionSyncRequest("token", "saved", 100)
        val accounts = sequenceOf("token" to listOf("configured"))
        val cursors = mutableMapOf("token" to "saved")

        service.processPlaidTransactions(accounts, cursors)
        assertFailsWith<IllegalStateException> {
            service.processPlaidTransactions(accounts, cursors, requireComplete = true)
        }
        assertEquals(mapOf("token" to "saved"), cursors)
    }

    companion object {
        private const val PLAID_BATCH_SIZE = 100
        private const val ALLOW_ITEM_TO_FAIL = true

        @JvmStatic
        fun provideGetTransactionSyncRequestTestCases(): Stream<Arguments> {
            return Stream.of(
                // Test case 1: With cursor
                Arguments.of(
                    "With cursor",
                    "token1",
                    "cursor1",
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token1",
                        cursor = "cursor1",
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),

                // Test case 2: Without cursor
                Arguments.of(
                    "Without cursor",
                    "token2",
                    null,
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token2",
                        cursor = null,
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),

                // Test case 3: Custom batch size
                Arguments.of(
                    "Custom batch size",
                    "token3",
                    "cursor3",
                    50, // Custom batch size
                    TransactionsSyncRequest(
                        accessToken = "token3",
                        cursor = "cursor3",
                        count = 50, // Custom batch size
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),

                // Test case 4: Empty cursor
                Arguments.of(
                    "Empty cursor",
                    "token4",
                    "",
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token4",
                        cursor = "",
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),

                // Test case 5: Very large batch size
                Arguments.of(
                    "Very large batch size",
                    "token5",
                    "cursor5",
                    1000, // Very large batch size
                    TransactionsSyncRequest(
                        accessToken = "token5",
                        cursor = "cursor5",
                        count = 1000, // Very large batch size
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),

                // Test case 6: Very small batch size
                Arguments.of(
                    "Very small batch size",
                    "token6",
                    "cursor6",
                    1, // Very small batch size
                    TransactionsSyncRequest(
                        accessToken = "token6",
                        cursor = "cursor6",
                        count = 1, // Very small batch size
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),

                // Test case 7: Long token and cursor
                Arguments.of(
                    "Long token and cursor",
                    "token7".repeat(10), // Long token
                    "cursor7".repeat(10), // Long cursor
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token7".repeat(10), // Long token
                        cursor = "cursor7".repeat(10), // Long cursor
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),

                // Test case 8: Special characters in token and cursor
                Arguments.of(
                    "Special characters in token and cursor",
                    "token8!@#$%^&*()",
                    "cursor8!@#$%^&*()",
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token8!@#$%^&*()",
                        cursor = "cursor8!@#$%^&*()",
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                )
            )
        }
    }

    private val plaidApiWrapper: PlaidApiWrapper = mock()
    private val plaidSyncService = PlaidSyncService(
        plaidApiWrapper,
        PLAID_BATCH_SIZE,
        ALLOW_ITEM_TO_FAIL
    )

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideGetTransactionSyncRequestTestCases")
    fun testGetTransactionSyncRequest(
        testName: String,
        accessToken: String,
        cursor: String?,
        batchSize: Int,
        expectedRequest: TransactionsSyncRequest
    ) {
        // Execute
        val request = plaidSyncService.getTransactionSyncRequest(accessToken, cursor, batchSize)

        // Verify
        assertEquals(expectedRequest.accessToken, request.accessToken)
        assertEquals(expectedRequest.cursor, request.cursor)
        assertEquals(expectedRequest.count, request.count)
        assertEquals(expectedRequest.options?.includeOriginalDescription, request.options?.includeOriginalDescription)
        assertEquals(
            expectedRequest.options?.includePersonalFinanceCategory,
            request.options?.includePersonalFinanceCategory
        )
    }
}
