package net.djvk.fireflyPlaidConnector2.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class RetryExecutorTest {
    private val fastRetry = RetryProperties(maxAttempts = 5, initialDelayMs = 0, maxDelayMs = 0)

    @ParameterizedTest
    @CsvSource(
        "429, 10, 15000",
        "429, 600, 600000",
        "503, 600, 600000",
        "503, 3601, 3600000",
        "503, 9223372036854775808, 3600000",
        "429, -1, 15000",
        "429, 'Wed, 01 Jan 2031 00:00:00 GMT', 15000",
    )
    fun usesNumericRetryAfterAsMinimumDelay(status: Int, header: String, expectedDelay: Long) = runBlocking {
        val engine = MockEngine {
            respond("", HttpStatusCode.fromValue(status), headersOf(HttpHeaders.RetryAfter, header))
        }
        HttpClient(engine) { expectSuccess = true }.use { client ->
            val stopBeforeWaiting = IllegalStateException("Stop before waiting")
            val thrown = assertFailsWith<IllegalStateException> {
                RetryExecutor.execute(
                    RetryProperties(jitterFactor = 0.0),
                    onRetry = { _, delayMs, _ ->
                        assertEquals(expectedDelay, delayMs)
                        throw stopBeforeWaiting
                    },
                ) {
                    client.get("https://api.test")
                }
            }
            assertTrue(thrown === stopBeforeWaiting)
        }
    }

    @Test
    fun retriesDnsFailureThenSucceeds() = runBlocking {
        var attempts = 0
        val result = RetryExecutor.execute(fastRetry) {
            if (++attempts < 3) throw java.nio.channels.UnresolvedAddressException()
            "resolved"
        }
        assertEquals("resolved", result)
        assertEquals(3, attempts)
    }

    @Test
    fun succeedsOnFirstAttempt() {
        runBlocking {
            var callCount = 0
            val result = RetryExecutor.execute(fastRetry, "test") {
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
            var retryCallbackCount = 0
            val result = RetryExecutor.execute(
                fastRetry,
                "test",
                onRetry = { _, _, _ -> retryCallbackCount++ },
            ) {
                callCount++
                if (callCount < 3) throw java.io.IOException("boom")
                "success"
            }
            assertEquals("success", result)
            assertEquals(3, callCount)
            assertEquals(2, retryCallbackCount)
        }
    }

    @Test
    fun exhaustsAttemptsAndRethrowsOriginalException() {
        runBlocking {
            var callCount = 0
            val original = java.io.IOException("persistent failure")
            val thrown = assertFailsWith<java.io.IOException> {
                RetryExecutor.execute(RetryProperties(maxAttempts = 3, initialDelayMs = 0)) {
                    callCount++
                    throw original
                }
            }
            assertEquals(3, callCount)
            assertTrue(thrown === original, "should rethrow the original exception instance, not a wrapper")
        }
    }

    @Test
    fun nonRetryableExceptionShortCircuits() {
        runBlocking {
            var callCount = 0
            val original = IllegalStateException("not retryable")
            val thrown = assertFailsWith<IllegalStateException> {
                RetryExecutor.execute(
                    fastRetry,
                    "test",
                    isRetryable = { false },
                ) {
                    callCount++
                    throw original
                }
            }
            assertEquals(1, callCount)
            assertTrue(thrown === original)
        }
    }

    @Test
    fun defaultMaxAttemptsUsedWhenNull() {
        runBlocking {
            var callCount = 0
            assertFailsWith<java.io.IOException> {
                RetryExecutor.execute(RetryProperties(maxAttempts = null, initialDelayMs = 0)) {
                    callCount++
                    throw java.io.IOException("boom")
                }
            }
            assertEquals(RetryExecutor.DEFAULT_MAX_ATTEMPTS, callCount)
        }
    }

    @Test
    fun computeDelayMsGrowsExponentiallyAndCapsAtMaxDelay() {
        val properties = RetryProperties(
            initialDelayMs = 1000,
            maxDelayMs = 5000,
            multiplier = 2.0,
            jitterFactor = 0.0,
        )
        assertEquals(1000, RetryExecutor.computeDelayMs(properties, 1))
        assertEquals(2000, RetryExecutor.computeDelayMs(properties, 2))
        assertEquals(4000, RetryExecutor.computeDelayMs(properties, 3))
        // Would be 8000 uncapped; must be capped at maxDelayMs
        assertEquals(5000, RetryExecutor.computeDelayMs(properties, 4))
    }

    @Test
    fun computeDelayMsAppliesJitterWithinBounds() {
        val properties = RetryProperties(
            initialDelayMs = 1000,
            maxDelayMs = 60000,
            multiplier = 2.0,
            jitterFactor = 0.5,
        )
        val random = Random(42)
        repeat(100) {
            val delayMs = RetryExecutor.computeDelayMs(properties, 2, random)
            // base for attempt 2 is 2000; jitter of 0.5 means bounds of [1000, 3000]
            assertTrue(delayMs in 1000..3000, "delay $delayMs out of expected jitter bounds")
        }
    }

    @Test
    fun defaultIsRetryableTreatsClientErrorsOtherThan429AsNonRetryable() {
        assertTrue(!RetryExecutor.defaultIsRetryable(IllegalStateException()))
    }
}
