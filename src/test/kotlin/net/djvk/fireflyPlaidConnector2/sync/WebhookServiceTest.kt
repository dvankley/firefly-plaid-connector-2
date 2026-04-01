package net.djvk.fireflyPlaidConnector2.sync

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.*

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidTransactionId
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.constants.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto


import java.time.Duration
import kotlin.test.assertContains

internal class WebhookServiceTest {
    // Lots more to learn: https://stackoverflow.com/a/35554077


    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }



    @Test
    fun testWebhookBasics() = runBlocking{
        // Arrange
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val url = server.url("/webhook").toString()
        val bearerToken = "test-token"
        val webhookService = WebhookService(url, bearerToken)

        // Act
        webhookService.addDataForHook(
            emptyList<TransactionRead>(),
            PlaidTransactionResult(
                emptyList<Transaction>(),
                emptyList<Transaction>(),
                emptyList<PlaidTransactionId>()),
            emptyList<FireflyTransactionDto>(),
            emptyList<FireflyTransactionDto>(),
            emptyList<FireflyTransactionId>(),
        )
        webhookService.post(loopDuration = Duration.ofMillis(1500)) // or whatever method you call


        // Assert
        val recorded = server.takeRequest()
        assertEquals("/webhook", recorded.path)
        assertEquals("Bearer $bearerToken", recorded.getHeader("Authorization"))
        assertEquals("application/json", recorded.getHeader("Content-Type"))

        val recvd = Json.decodeFromString<JsonObject>(recorded.body.readUtf8())
        assertContains(recvd.toMap(), "data")
    }
}
