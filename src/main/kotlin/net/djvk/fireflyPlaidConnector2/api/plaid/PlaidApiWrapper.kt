package net.djvk.fireflyPlaidConnector2.api.plaid

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import net.djvk.fireflyPlaidConnector2.api.plaid.apis.PlaidApi
import net.djvk.fireflyPlaidConnector2.api.plaid.infrastructure.clientIdHeader
import net.djvk.fireflyPlaidConnector2.api.plaid.infrastructure.secretHeader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.minutes

/**
 * A wrapper for Plaid API calls that provides additional services:
 *  - rate limiting
 *  - retry logic
 *  - error handling
 */
@Component
class PlaidApiWrapper(
    private val plaidApi: PlaidApi,
    @Value("\${fireflyPlaidConnector2.plaid.maxRetries:3}")
    private val maxRetries: Int,
    @Value("\${fireflyPlaidConnector2.plaid.clientId}")
    private val plaidClientId: String,
    @Value("\${fireflyPlaidConnector2.plaid.secret}")
    private val plaidSecret: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        plaidApi.setApiKey(plaidClientId, clientIdHeader)
        plaidApi.setApiKey(plaidSecret, secretHeader)
    }

    /**
     * Executes a request to the Plaid API
     *
     * @param logString String to include in log messages
     */
    suspend fun <T> executeRequest(
        request: suspend (PlaidApi) -> T,
        logString: String,
        remainingRetries: Int = maxRetries,
    ): T {
        if (remainingRetries <= 0) {
            throw RuntimeException("Plaid API call $logString failed after $maxRetries retries")
        }
        try {
            return request(plaidApi)
        } catch (cre: ClientRequestException) {
            if (cre.response.status == HttpStatusCode.TooManyRequests) {
                logger.error("429 rate limiting error encountered while calling $logString. Waiting for one minute.", cre)
                delay(1.minutes)
                return executeRequest(request, logString, remainingRetries)
            }
            throw cre
        } catch (e: Throwable) {
            logger.error("Error encountered $logString.", e)
            return executeRequest(request, logString, remainingRetries - 1)
        }
    }
}