package net.djvk.fireflyPlaidConnector2.api.plaid

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import net.djvk.fireflyPlaidConnector2.api.RetryExecutor
import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import net.djvk.fireflyPlaidConnector2.api.plaid.apis.PlaidApi
import net.djvk.fireflyPlaidConnector2.api.plaid.infrastructure.ApiClient
import net.djvk.fireflyPlaidConnector2.config.properties.PlaidApiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

typealias PlaidTransactionId = String

const val clientIdHeader = "PLAID-CLIENT-ID"
const val secretHeader = "PLAID-SECRET"

/**
 * A wrapper for Plaid API calls that provides additional services:
 *  - rate limiting
 *  - retry logic
 *  - error handling
 */
@Component
class PlaidApiWrapper(
    properties: PlaidApiProperties,
    @Qualifier("plaidRetryProperties")
    plaidRetryProperties: RetryProperties,
    httpClientEngine: HttpClientEngine? = null,
    @Qualifier("plaidClientConfig")
    httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
) {
    private val plaidApi = PlaidApi(properties.url, httpClientEngine, httpClientConfig) {
        ApiClient.JSON_DEFAULT.invoke(this)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    }
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val retryProperties: RetryProperties = when {
        plaidRetryProperties.maxAttempts != null -> plaidRetryProperties
        properties.maxRetries != null -> {
            val legacyMaxRetries = properties.maxRetries
            logger.warn(
                "fireflyPlaidConnector2.plaid.maxRetries is deprecated; use " +
                        "fireflyPlaidConnector2.plaid.retry.maxAttempts instead. Inferring " +
                        "maxAttempts = maxRetries + 1 = ${legacyMaxRetries + 1}."
            )
            plaidRetryProperties.copy(maxAttempts = legacyMaxRetries + 1)
        }
        else -> plaidRetryProperties
    }

    init {
        plaidApi.setApiKey(properties.clientId, clientIdHeader)
        plaidApi.setApiKey(properties.secret, secretHeader)
    }

    /**
     * Executes a request to the Plaid API
     *
     * @param logString String to include in log messages
     */
    suspend fun <T> executeRequest(
        request: suspend (PlaidApi) -> T,
        logString: String,
    ): T {
        return RetryExecutor.execute(
            retryProperties,
            logString,
            onRetry = { attempt, delayMs, error ->
                logger.warn("Plaid API call '$logString' failed on attempt $attempt, retrying in ${delayMs}ms", error)
            },
        ) { request(plaidApi) }
    }
}
