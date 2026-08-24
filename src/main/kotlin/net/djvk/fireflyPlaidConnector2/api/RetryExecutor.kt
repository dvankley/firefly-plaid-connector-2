package net.djvk.fireflyPlaidConnector2.api

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.util.Random
import kotlin.math.min
import kotlin.math.pow

/**
 * Generic exponential backoff retry executor shared by Plaid and Firefly API call wrappers.
 *
 * On exhaustion, the original last exception is rethrown unchanged (not wrapped), so existing
 * `catch` clauses typed to a specific exception (e.g. `ClientRequestException`) keep working
 * exactly as if retries had never been attempted.
 */
object RetryExecutor {
    const val DEFAULT_MAX_ATTEMPTS = 5

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun <T> execute(
        properties: RetryProperties,
        logString: String = "",
        isRetryable: (Throwable) -> Boolean = ::defaultIsRetryable,
        onRetry: (attempt: Int, delayMs: Long, error: Throwable) -> Unit = { attempt, delayMs, error ->
            logger.warn("API call '$logString' failed on attempt $attempt, retrying in ${delayMs}ms", error)
        },
        block: suspend () -> T,
    ): T {
        val maxAttempts = properties.maxAttempts ?: DEFAULT_MAX_ATTEMPTS
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e
                }
                val delayMs = computeDelayMs(properties, attempt)
                onRetry(attempt, delayMs, e)
                delay(delayMs)
                attempt++
            }
        }
    }

    /**
     * Computes the exponential-backoff delay (ms) for the given 1-based attempt number,
     * i.e. the delay to wait *after* that attempt has failed and before the next one.
     */
    fun computeDelayMs(properties: RetryProperties, attempt: Int, random: Random = Random()): Long {
        val base = properties.initialDelayMs * properties.multiplier.pow(attempt - 1)
        val capped = min(base, properties.maxDelayMs.toDouble())
        if (properties.jitterFactor <= 0.0) {
            return capped.toLong()
        }
        val jitterRange = capped * properties.jitterFactor
        val jittered = capped + (random.nextDouble() * 2 - 1) * jitterRange
        return jittered.toLong().coerceIn(0, properties.maxDelayMs)
    }

    fun defaultIsRetryable(t: Throwable): Boolean {
        return when (t) {
            is ClientRequestException -> t.response.status == HttpStatusCode.TooManyRequests
            is ServerResponseException -> true
            is ConnectTimeoutException -> true
            is SocketTimeoutException -> true
            is HttpRequestTimeoutException -> true
            is IOException -> true
            is UnresolvedAddressException -> true
            else -> false
        }
    }
}
