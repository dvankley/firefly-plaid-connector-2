package net.djvk.fireflyPlaidConnector2.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Firefly API connection settings. Retry tuning is bound separately; see
 * [net.djvk.fireflyPlaidConnector2.config.RetryPropertiesConfig].
 *
 * The generated Firefly API clients still read `fireflyPlaidConnector2.firefly.url` themselves; it's bound here
 * too so a missing URL fails at startup with the other Firefly settings.
 */
@ConfigurationProperties(prefix = "firefly-plaid-connector2.firefly")
data class FireflyApiProperties(
    val url: String,
    val personalAccessToken: String,
    val requestTimeoutMillis: Long = 60000,
) {
    override fun toString() =
        "FireflyApiProperties(url=$url, personalAccessToken=<redacted>, requestTimeoutMillis=$requestTimeoutMillis)"
}
