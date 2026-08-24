package net.djvk.fireflyPlaidConnector2.api.firefly

import net.djvk.fireflyPlaidConnector2.api.RetryExecutor
import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * A wrapper for Firefly API calls that provides retry logic with configurable exponential backoff.
 */
@Component
class FireflyApiWrapper(
    @Qualifier("fireflyRetryProperties")
    private val retryProperties: RetryProperties,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Executes [block], retrying on retryable failures per `fireflyPlaidConnector2.firefly.retry`.
     * If retries are exhausted, the original exception propagates unchanged.
     */
    suspend fun <T> executeRequest(
        logString: String,
        isRetryable: (Throwable) -> Boolean = RetryExecutor::defaultIsRetryable,
        block: suspend () -> T,
    ): T {
        return RetryExecutor.execute(
            retryProperties,
            logString,
            isRetryable,
            onRetry = { attempt, delayMs, error ->
                logger.warn("Firefly API call '$logString' failed on attempt $attempt, retrying in ${delayMs}ms", error)
            },
        ) { block() }
    }
}
