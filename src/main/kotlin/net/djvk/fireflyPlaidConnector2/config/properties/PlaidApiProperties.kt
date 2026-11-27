package net.djvk.fireflyPlaidConnector2.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Plaid API connection settings. Retry tuning is bound separately; see
 * [net.djvk.fireflyPlaidConnector2.config.RetryPropertiesConfig].
 */
@ConfigurationProperties(prefix = "firefly-plaid-connector2.plaid")
data class PlaidApiProperties(
    val url: String,
    val clientId: String,
    val secret: String,
    /**
     * Deprecated: use `fireflyPlaidConnector2.plaid.retry.maxAttempts` instead. If `maxAttempts`
     * isn't set, it's inferred as `maxRetries + 1` (maxRetries counted retries after the first
     * attempt; maxAttempts counts the first attempt too).
     */
    val maxRetries: Int? = null,
) {
    override fun toString() = "PlaidApiProperties(url=$url, clientId=<redacted>, secret=<redacted>, maxRetries=$maxRetries)"
}
