package net.djvk.fireflyPlaidConnector2.sync

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapperTest.Companion.mockPlaid
import net.djvk.fireflyPlaidConnector2.api.plaid.models.RemovedTransaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequestOptions
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncResponse
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsUpdateStatus
import net.djvk.fireflyPlaidConnector2.config.properties.PlaidTransactionProperties
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidMock
import net.djvk.fireflyPlaidConnector2.lib.createPlaidResponse
import net.djvk.fireflyPlaidConnector2.lib.testPolledProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
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
    private val mapper = ObjectMapper()

    @Test
    fun `initial backfill respects both date boundaries while saved cursors still get incremental updates`() = runBlocking {
        val cutoff = LocalDate.of(2025, 1, 10)
        val through = cutoff.plusDays(4)
        val plaid = PlaidMock()
        val service = PlaidSyncService(
            plaid.wrapper,
            PlaidTransactionProperties(100),
            testPolledProperties(initialBackfillAfter = cutoff, initialBackfillThrough = through),
        )
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
        val service = spy(PlaidSyncService(wrapper, PlaidTransactionProperties(100), testPolledProperties()))
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
        assertEquals(listOf("included1", "included2"), result.deleted.map { it.transactionId })
        assertEquals("done", cursors["token"])
    }

    @Test
    fun `second-page failure discards all staged changes and lets the healthy item advance`() = runBlocking {
        val service = syncService(
            syncPage(
                "failed", "saved", "page-two", hasMore = true,
                added = listOf("discard-add"),
                modified = listOf("discard-modification"),
                removed = listOf("discard-removal"),
            ),
            syncError("failed", "page-two"),
            syncPage("healthy", "healthy-saved", "healthy-next", added = listOf("keep-add")),
        )
        val items = sequenceOf("failed" to listOf("mapped"), "healthy" to listOf("mapped"))
        val cursors = mutableMapOf("failed" to "saved", "healthy" to "healthy-saved")

        val result = service.processPlaidTransactions(items, cursors)

        assertEquals(listOf("keep-add"), result.created.map { it.transactionId })
        assertEquals(emptyList<Transaction>(), result.updated)
        assertEquals(0, result.deleted.size)
        assertEquals(mapOf("failed" to "saved", "healthy" to "healthy-next"), cursors)
    }

    @Test
    fun `interrupted catch-up retries on a later poll and skips history`() = runBlocking {
        val service = syncService(
            syncPage("fresh", null, "page-two", hasMore = true, added = listOf("history")),
            syncError("fresh", "page-two"),
            syncPage("healthy", null, "healthy-started", added = listOf("history")),
            syncPage("fresh", null, ""),
            syncPage("healthy", "healthy-started", "healthy-next"),
            syncPage("fresh", null, "retry-page-two", hasMore = true, added = listOf("history")),
            syncPage("fresh", "retry-page-two", "fresh-started", added = listOf("history-two")),
            syncPage("healthy", "healthy-next", "healthy-final", added = listOf("current")),
        )
        val items = sequenceOf("fresh" to listOf("mapped"), "healthy" to listOf("mapped"))
        val cursors = mutableMapOf<String, String>()

        service.initializeCursors(items, cursors)
        assertEquals(mapOf("healthy" to "healthy-started"), cursors)

        assertEquals(PlaidTransactionResult(emptyList(), emptyList(), emptyList()), service.processPlaidTransactions(items, cursors))
        assertEquals(mapOf("healthy" to "healthy-next"), cursors)

        val result = service.processPlaidTransactions(items, cursors)
        assertEquals(listOf("current"), result.created.map { it.transactionId })
        assertEquals(mapOf("fresh" to "fresh-started", "healthy" to "healthy-final"), cursors)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["saved"])
    fun `pagination mutation discards the first attempt and restarts from the original cursor`(startingCursor: String?) = runBlocking {
        val service = syncService(
            syncPage("item", startingCursor, "stale-page-two", hasMore = true, added = listOf("stale")),
            syncError("item", "stale-page-two", MUTATION_ERROR),
            syncPage("item", startingCursor, "replay-page-two", hasMore = true, added = listOf("replayed")),
            syncPage("item", "replay-page-two", "complete"),
            allowItemToFail = false,
            initialBackfillAfter = LocalDate.of(2025, 1, 10),
        )
        val cursors = mutableMapOf<String, String>()
        if (startingCursor != null) cursors["item"] = startingCursor

        val result = service.processPlaidTransactions(sequenceOf("item" to listOf("mapped")), cursors)

        assertEquals(listOf("replayed"), result.created.map { it.transactionId })
        assertEquals(mapOf("item" to "complete"), cursors)
    }

    @Test
    fun `repeated pagination mutations exhaust the restart limit and skip the item`() = runBlocking {
        val page = syncPage("item", "saved", "page-two", hasMore = true, added = listOf("stale"))
        val mutation = syncError("item", "page-two", MUTATION_ERROR)
        val service = syncService(page, mutation, page, mutation, page, mutation)
        val cursors = mutableMapOf("item" to "saved")

        val result = service.processPlaidTransactions(sequenceOf("item" to listOf("mapped")), cursors)

        assertEquals(PlaidTransactionResult(emptyList(), emptyList(), emptyList()), result)
        assertEquals(mapOf("item" to "saved"), cursors)
    }

    @Test
    fun recoveryRequiresPlaidItemsToSucceedEvenWithAllowItemToFail() = runBlocking {
        val service = syncService(
            syncPage("item", "saved", "page-two", hasMore = true, added = listOf("first")),
            syncError("item", "page-two"),
        )
        val cursors = mutableMapOf("item" to "saved")

        val failure = assertFailsWith<IllegalStateException> {
            service.processPlaidTransactions(sequenceOf("item" to listOf("mapped")), cursors, requireComplete = true)
        }
        assertEquals(HttpStatusCode.BadRequest, (failure.cause as ClientRequestException).response.status)
        assertEquals(mapOf("item" to "saved"), cursors)
    }

    private fun transactionJson(id: String) = mapOf(
        "account_id" to "mapped",
        "amount" to 1.0,
        "date" to "2025-01-11",
        "location" to emptyMap<String, String>(),
        "name" to id,
        "payment_meta" to emptyMap<String, String>(),
        "pending" to false,
        "transaction_id" to id,
        "payment_channel" to "other",
    )

    private data class SyncExchange(
        val item: String,
        val cursor: String?,
        val status: HttpStatusCode,
        val body: String,
    )

    private fun syncPage(
        item: String,
        cursor: String?,
        nextCursor: String,
        hasMore: Boolean = false,
        added: List<String> = emptyList(),
        modified: List<String> = emptyList(),
        removed: List<String> = emptyList(),
    ): SyncExchange {
        val body = mapper.writeValueAsString(mapOf(
            "transactions_update_status" to "HISTORICAL_UPDATE_COMPLETE",
            "accounts" to emptyList<Any>(),
            "added" to added.map(::transactionJson),
            "modified" to modified.map(::transactionJson),
            "removed" to removed.map { mapOf("account_id" to "mapped", "transaction_id" to it) },
            "next_cursor" to nextCursor,
            "has_more" to hasMore,
            "request_id" to "request",
        ))
        return SyncExchange(item, cursor, HttpStatusCode.OK, body)
    }

    private fun syncError(item: String, cursor: String, code: String = "ITEM_LOGIN_REQUIRED") =
        SyncExchange(item, cursor, HttpStatusCode.BadRequest, """{"error_code":"$code"}""")

    private fun syncService(
        vararg exchanges: SyncExchange,
        allowItemToFail: Boolean = true,
        initialBackfillAfter: LocalDate? = null,
    ): PlaidSyncService {
        val remaining = exchanges.iterator()
        val engine = MockEngine { request ->
            val expected = remaining.next()
            val body = mapper.readTree(request.body.toByteArray())
            assertEquals(expected.item, body.path("access_token").asText())
            assertEquals(expected.cursor, body.path("cursor").asText(null))
            respond(expected.body, expected.status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return PlaidSyncService(
            mockPlaid(engine),
            PlaidTransactionProperties(100),
            testPolledProperties(allowItemToFail = allowItemToFail, initialBackfillAfter = initialBackfillAfter),
        )
    }

    companion object {
        private const val MUTATION_ERROR = "TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION"
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
        PlaidTransactionProperties(PLAID_BATCH_SIZE),
        testPolledProperties(allowItemToFail = ALLOW_ITEM_TO_FAIL),
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
