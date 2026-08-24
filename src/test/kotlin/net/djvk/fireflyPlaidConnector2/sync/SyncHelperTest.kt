package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.engine.mock.*
import io.ktor.client.network.sockets.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.ApiConfiguration
import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AboutApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AccountsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class SyncHelperTest {
    private val fireflyAboutApi: AboutApi = mock()
    private val fireflyTxApi: TransactionsApi = mock()
    private val fireflyAccountsApi: AccountsApi = mock()

    /**
     * A real [FireflyApiWrapper] (not mocked) with fast, bounded retry settings, so these tests
     * exercise the actual retry-then-propagate behavior rather than a pass-through stub.
     */
    private fun syncHelper(maxAttempts: Int = 3): SyncHelper {
        return SyncHelper(
            plaidAccountsConfig = AccountConfigs(emptyList()),
            fireflyAccessToken = "testToken",
            fireflyAboutApi = fireflyAboutApi,
            fireflyTxApi = fireflyTxApi,
            fireflyAccountsApi = fireflyAccountsApi,
            fireflyApiWrapper = FireflyApiWrapper(RetryProperties(maxAttempts = maxAttempts, initialDelayMs = 0)),
        )
    }

    private fun dto(externalId: String = "ext1", amount: String = "100.0") = FireflyTransactionDto(
        id = null,
        tx = FireflyFixtures.getTransaction(externalId = externalId, amount = amount).transactions.first(),
    )

    @Test
    fun optimisticInsertRetriesTimeoutThenSucceeds() {
        runBlocking {
            val tx = dto()
            var callCount = 0
            whenever(fireflyTxApi.storeTransaction(any())).thenAnswer {
                callCount++
                if (callCount < 3) throw ConnectTimeoutException("timeout", null)
                mock()
            }

            syncHelper().optimisticInsertBatchIntoFirefly(listOf(tx))

            assertEquals(3, callCount)
        }
    }

    @Test
    fun optimisticInsertPropagatesWhenTimeoutRetriesExhausted() {
        runBlocking {
            val tx = dto()
            var callCount = 0
            whenever(fireflyTxApi.storeTransaction(any())).thenAnswer {
                callCount++
                throw ConnectTimeoutException("timeout", null)
            }

            assertFailsWith<ConnectTimeoutException> {
                syncHelper(maxAttempts = 2).optimisticInsertBatchIntoFirefly(listOf(tx))
            }
            assertEquals(2, callCount, "should not silently swallow the timeout after exhausting retries")
        }
    }

    @Test
    fun updateBatchRetriesThenSucceeds() {
        runBlocking {
            val tx = FireflyTransactionDto(
                id = "tx-id-1",
                tx = FireflyFixtures.getTransaction().transactions.first(),
            )
            var callCount = 0
            whenever(fireflyTxApi.updateTransaction(any(), any())).thenAnswer {
                callCount++
                if (callCount < 2) throw ConnectTimeoutException("timeout", null)
                mock()
            }

            syncHelper().updateBatchInFirefly(listOf(tx))

            assertEquals(2, callCount)
        }
    }

    @Test
    fun updateBatchPropagatesWhenRetriesExhausted() {
        runBlocking {
            val tx = FireflyTransactionDto(
                id = "tx-id-1",
                tx = FireflyFixtures.getTransaction().transactions.first(),
            )
            whenever(fireflyTxApi.updateTransaction(any(), any())).thenAnswer {
                throw ConnectTimeoutException("timeout", null)
            }

            assertFailsWith<ConnectTimeoutException> {
                syncHelper(maxAttempts = 2).updateBatchInFirefly(listOf(tx))
            }
        }
    }

    @Test
    fun deleteBatchRetriesThenSucceeds() {
        runBlocking {
            var callCount = 0
            whenever(fireflyTxApi.deleteTransaction(any())).thenAnswer {
                callCount++
                if (callCount < 2) throw ConnectTimeoutException("timeout", null)
                mock()
            }

            syncHelper().deleteBatchInFirefly(listOf("tx-id-1"))

            assertEquals(2, callCount)
        }
    }

    @Test
    fun deleteBatchPropagatesWhenRetriesExhausted() {
        runBlocking {
            whenever(fireflyTxApi.deleteTransaction(any())).thenAnswer {
                throw ConnectTimeoutException("timeout", null)
            }

            assertFailsWith<ConnectTimeoutException> {
                syncHelper(maxAttempts = 2).deleteBatchInFirefly(listOf("tx-id-1"))
            }
        }
    }

    /**
     * A delete can succeed on the Firefly server but still surface as a client-side failure if the
     * response never arrives (e.g. a dropped connection), which would otherwise trigger a retry
     * against an already-deleted transaction. Firefly reports that as 404, which should be treated
     * as success (the desired end state - the transaction doesn't exist - is already achieved).
     *
     * Uses a real [TransactionsApi] over a Ktor [MockEngine] (rather than a Mockito stub) so the
     * [ClientRequestException] thrown is constructed by Ktor itself, avoiding hand-rolling a
     * response/call chain that the exception's message computation depends on.
     */
    @Test
    fun deleteBatchTreatsNotFoundAsSuccess() {
        runBlocking {
            var callCount = 0
            val realTxApi = TransactionsApi(
                baseUrl = "https://firefly.test",
                httpClientEngine = MockEngine {
                    callCount++
                    respond(
                        content = ByteReadChannel("""{"message":"not found"}"""),
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
                httpClientConfig = ApiConfiguration().getFireflyClientConfig(60000),
            )
            val helper = SyncHelper(
                plaidAccountsConfig = AccountConfigs(emptyList()),
                fireflyAccessToken = "testToken",
                fireflyAboutApi = fireflyAboutApi,
                fireflyTxApi = realTxApi,
                fireflyAccountsApi = fireflyAccountsApi,
                fireflyApiWrapper = FireflyApiWrapper(RetryProperties(maxAttempts = 3, initialDelayMs = 0)),
            )

            helper.deleteBatchInFirefly(listOf("tx-id-1"))

            assertEquals(1, callCount, "404 should not be retried, and should not throw")
        }
    }
}
