package net.djvk.fireflyPlaidConnector2.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Controls tagging Firefly transactions with Plaid's personal finance categories.
 */
@ConfigurationProperties(prefix = "firefly-plaid-connector2.categorization")
data class CategorizationProperties(
    val primary: Primary = Primary(),
    val detailed: Detailed = Detailed(),
) {
    data class Primary(
        val enable: Boolean = false,
        val prefix: String = "plaid-primary-cat-",
    )

    data class Detailed(
        val enable: Boolean = false,
        val prefix: String = "plaid-detailed-cat-",
    )
}
