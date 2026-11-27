package net.djvk.fireflyPlaidConnector2.api.firefly

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.ApiConfiguration
import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class FireflyApiWrapperTest {
    private val fastRetryProperties = RetryProperties(maxAttempts = 3, initialDelayMs = 0)

    private fun wrapper(properties: RetryProperties = fastRetryProperties) = FireflyApiWrapper(properties)

    @Test
    fun retriesWhenReadingASuccessfulResponseFails() = runBlocking {
        var attempts = 0
        val engine = MockEngine {
            if (++attempts == 1) {
                val body = ByteChannel().apply { cancel(IOException("Connection dropped during body read")) }
                respond(body)
            } else {
                respond("complete response")
            }
        }
        HttpClient(engine, ApiConfiguration.fireflyClientConfig(60000)).use { client ->
            val result = wrapper().executeRequest("test") {
                client.get("https://firefly.test").bodyAsText()
            }
            assertEquals("complete response", result)
            assertEquals(2, attempts)
        }
    }

    @Test
    fun passthroughSuccess() {
        runBlocking {
            var callCount = 0
            val result = wrapper().executeRequest("test") {
                callCount++
                "success"
            }
            assertEquals("success", result)
            assertEquals(1, callCount)
        }
    }

    @Test
    fun retriesThenSucceeds() {
        runBlocking {
            var callCount = 0
            val result = wrapper().executeRequest("test") {
                callCount++
                if (callCount < 3) throw java.io.IOException("boom")
                "success"
            }
            assertEquals("success", result)
            assertEquals(3, callCount)
        }
    }

    @Test
    fun exhaustedRetriesRethrowOriginalException() {
        runBlocking {
            var callCount = 0
            val original = java.io.IOException("persistent")
            val thrown = assertFailsWith<java.io.IOException> {
                wrapper(RetryProperties(maxAttempts = 2, initialDelayMs = 0)).executeRequest("test") {
                    callCount++
                    throw original
                }
            }
            assertEquals(2, callCount)
            assertTrue(thrown === original)
        }
    }

    @Test
    fun nonRetryableExceptionShortCircuits() {
        runBlocking {
            var callCount = 0
            val original = IllegalStateException("duplicate of transaction")

            val thrown = assertFailsWith<IllegalStateException> {
                wrapper().executeRequest("test") {
                    callCount++
                    throw original
                }
            }
            assertEquals(1, callCount, "non-retryable exception should not be retried")
            assertTrue(thrown === original)
        }
    }
}
