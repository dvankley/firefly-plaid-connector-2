package net.djvk.fireflyPlaidConnector2.api.plaid

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.ApiConfiguration
import net.djvk.fireflyPlaidConnector2.api.plaid.models.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

internal class PlaidApiWrapperTest {
    companion object {
        val httpClientConfig = ApiConfiguration().getClientConfig()

        val baseDate = LocalDate.of(2024, 6, 24)
        val testBaseUrl = "https://plaid.test"
        val testClientId = "testClientId"
        val testSecret = "testSecret"
        val testAccessToken = "testAccessToken"
        val testAccount1 = "aaa"
        val testAccount2 = "bbb"

        val getTransactionsRequest = TransactionsGetRequest(
            accessToken = testAccessToken,
            startDate = baseDate.minusDays(30),
            endDate = baseDate,
            options = TransactionsGetRequestOptions(
                accountIds = listOf(testAccount1, testAccount2),
                count = 100,
                offset = 0,
                includeOriginalDescription = true,
                includePersonalFinanceCategoryBeta = false,
                includePersonalFinanceCategory = true,
            ),
        )
        val getTransactionsResponseStr = this::class.java.classLoader
            .getResource("plaid/response-get-transactions.json")!!
            .readText(Charsets.UTF_8)

        val syncTransactionsRequest = TransactionsSyncRequest(
            accessToken = testAccessToken,
            options = TransactionsSyncRequestOptions(
                includeOriginalDescription = true,
                includePersonalFinanceCategory = true,
            ),
        )
        val syncTransactionsResponseStr = this::class.java.classLoader
            .getResource("plaid/response-sync-transactions.json")!!
            .readText(Charsets.UTF_8)

        val getBalanceRequest = AccountsBalanceGetRequest(
            accessToken = testAccessToken,
            options = AccountsBalanceGetRequestOptions(listOf(testAccount1, testAccount2)),
        )
        val getBalanceResponseStr = this::class.java.classLoader
            .getResource("plaid/response-balance.json")!!
            .readText(Charsets.UTF_8)

        fun mockPlaid(engine: MockEngine): PlaidApiWrapper {
            return PlaidApiWrapper(
                baseUrl = testBaseUrl,
                maxRetries = 1,
                plaidClientId = testClientId,
                plaidSecret = testSecret,
                httpClientEngine = engine,
                httpClientConfig = httpClientConfig,
            )
        }
    }

    @Test
    fun plaidGetTransactionsRequestIsWellFormed() {
        runBlocking {
            val plaid = mockPlaid(MockEngine { request ->
                assertEquals(testBaseUrl, request.url.toString().substring(0, testBaseUrl.length))
                assertEquals(listOf(testClientId), request.headers.getAll("PLAID-CLIENT-ID"))
                assertEquals(listOf(testSecret), request.headers.getAll("PLAID-SECRET"))
                assertEquals(listOf("application/json"), request.headers.getAll("Accept"))
                assertEquals(listOf("UTF-8"), request.headers.getAll("Accept-Charset"))
                assertEquals(4, request.headers.entries().size, "Number of request headers")

                assertEquals("""
                    {
                      "access_token" : "${testAccessToken}",
                      "start_date" : "2024-05-25",
                      "end_date" : "2024-06-24",
                      "client_id" : null,
                      "options" : {
                        "account_ids" : [ "${testAccount1}", "${testAccount2}" ],
                        "count" : 100,
                        "offset" : 0,
                        "include_original_description" : true,
                        "include_personal_finance_category_beta" : false,
                        "include_personal_finance_category" : true
                      },
                      "secret" : null
                    }
                """.trimIndent(), request.body.toByteArray().toString(Charsets.UTF_8))

                respond(
                    content = ByteReadChannel(getTransactionsResponseStr),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val request = TransactionsGetRequest(
                accessToken = testAccessToken,
                startDate = baseDate.minusDays(30),
                endDate = baseDate,
                options = TransactionsGetRequestOptions(
                    listOf(testAccount1, testAccount2),
                    100,
                    0,
                    includeOriginalDescription = true,
                    includePersonalFinanceCategoryBeta = false,
                    includePersonalFinanceCategory = true,
                )
            )

            plaid.executeRequest(
                { plaidApi -> plaidApi.transactionsGet(request) },
                "transaction get request"
            )
        }
    }

    @Test
    fun plaidGetTransactionsResponseIsParsed() {
        runBlocking {
            val plaid = mockPlaid(MockEngine { request ->
                respond(
                    content = ByteReadChannel(getTransactionsResponseStr),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val response = plaid.executeRequest(
                { plaidApi -> plaidApi.transactionsGet(getTransactionsRequest) },
                "transaction get request"
            )
            assertEquals(HttpStatusCode.OK, response.response.status)

            val body = response.body()
            assertEquals(8, body.transactions.size, "number of transactions")
            assertEquals(2, body.accounts.size, "number of accounts")
        }
    }

    @Test
    fun plaidGetTransactionsItemLoginRequired() {
        runBlocking {
            val plaid = mockPlaid(MockEngine { request ->
                respond(
                    content = ByteReadChannel("""
                        {
                          "display_message": null,
                          "error_code": "ITEM_LOGIN_REQUIRED",
                          "error_message": "the login details of this item have changed (credentials, MFA, or required user action) and a user login is required to update this information. use Link's update mode to restore the item to a good state",
                          "error_type": "ITEM_ERROR",
                          "request_id": "abc123",
                          "suggested_action": null
                        }
                    """.trimIndent()),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val exception = assertFailsWith<ClientRequestException> {
                plaid.executeRequest(
                    { plaidApi -> plaidApi.transactionsGet(getTransactionsRequest) },
                    "transaction get request"
                )
            }

            assertEquals(HttpStatusCode.BadRequest, exception.response.status)
            assertContains(exception.message, "ITEM_LOGIN_REQUIRED")
            assertContains(exception.message, "the login details of this item have changed")
        }
    }

    @Test
    fun plaidSyncTransactionsRequestIsWellFormed() {
        runBlocking {
            val testCursor = "aabbcc112233"

            val plaid = mockPlaid(MockEngine { request ->
                assertEquals(testBaseUrl, request.url.toString().substring(0, testBaseUrl.length))
                assertEquals(listOf(testClientId), request.headers.getAll("PLAID-CLIENT-ID"))
                assertEquals(listOf(testSecret), request.headers.getAll("PLAID-SECRET"))
                assertEquals(listOf("application/json"), request.headers.getAll("Accept"))
                assertEquals(listOf("UTF-8"), request.headers.getAll("Accept-Charset"))
                assertEquals(4, request.headers.entries().size, "Number of request headers")

                assertEquals("""
                    {
                      "access_token" : "${testAccessToken}",
                      "client_id" : null,
                      "secret" : null,
                      "cursor" : "${testCursor}",
                      "count" : 100,
                      "options" : {
                        "include_original_description" : true,
                        "include_personal_finance_category" : true
                      }
                    }
                """.trimIndent(), request.body.toByteArray().toString(Charsets.UTF_8))

                respond(
                    content = ByteReadChannel(syncTransactionsResponseStr),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val request = TransactionsSyncRequest(
                testAccessToken,
                cursor = testCursor,
                options = TransactionsSyncRequestOptions(
                    includeOriginalDescription = true,
                    includePersonalFinanceCategory = true,
                )
            )

            plaid.executeRequest(
                { plaidApi -> plaidApi.transactionsSync(request) },
                "transaction sync request"
            )
        }
    }

    @Test
    fun plaidSyncTransactionsResponseIsParsed() {
        runBlocking {
            val plaid = mockPlaid(MockEngine { request ->
                respond(
                    content = ByteReadChannel(syncTransactionsResponseStr),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val response = plaid.executeRequest(
                { plaidApi -> plaidApi.transactionsSync(syncTransactionsRequest) },
                "transaction sync request"
            )
            assertEquals(HttpStatusCode.OK, response.response.status)

            val body = response.body()
            assertEquals(24, body.added.size, "number of added transactions")
            assertEquals(0, body.modified.size, "number of modified transactions")
            assertEquals(0, body.removed.size, "number of removed transactions")
            assertEquals(false, body.hasMore, "hasMore")
            assertEquals("xyz098", body.nextCursor, "nextCursor")
        }
    }

    @Test
    fun plaidBalanceTransactionsRequestIsWellFormed() {
        runBlocking {
            val plaid = mockPlaid(MockEngine { request ->
                assertEquals(testBaseUrl, request.url.toString().substring(0, testBaseUrl.length))
                assertEquals(listOf(testClientId), request.headers.getAll("PLAID-CLIENT-ID"))
                assertEquals(listOf(testSecret), request.headers.getAll("PLAID-SECRET"))
                assertEquals(listOf("application/json"), request.headers.getAll("Accept"))
                assertEquals(listOf("UTF-8"), request.headers.getAll("Accept-Charset"))
                assertEquals(4, request.headers.entries().size, "Number of request headers")

                assertEquals("""
                    {
                      "access_token" : "${testAccessToken}",
                      "secret" : null,
                      "client_id" : null,
                      "options" : {
                        "account_ids" : [ "${testAccount1}", "${testAccount2}" ],
                        "min_last_updated_datetime" : null
                      }
                    }
                """.trimIndent(), request.body.toByteArray().toString(Charsets.UTF_8))

                respond(
                    content = ByteReadChannel(getBalanceResponseStr),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val request = AccountsBalanceGetRequest(
                accessToken = testAccessToken,
                options = AccountsBalanceGetRequestOptions(listOf(testAccount1, testAccount2)),
            )

            plaid.executeRequest(
                { plaidApi -> plaidApi.accountsBalanceGet(request) },
                "get account balance request"
            )
        }
    }

    @Test
    fun plaidBalanceTransactionsResponseIsParsed() {
        runBlocking {
            val plaid = mockPlaid(MockEngine { request ->
                respond(
                    content = ByteReadChannel(getBalanceResponseStr),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val response = plaid.executeRequest(
                { plaidApi -> plaidApi.accountsBalanceGet(getBalanceRequest) },
                "get account balance request"
            )
            assertEquals(HttpStatusCode.OK, response.response.status)

            val body = response.body()
            assertEquals(2, body.accounts.size, "number of accounts")
            assertNotNull(body.item, "item")
        }
    }
}