package net.djvk.fireflyPlaidConnector2.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "firefly-plaid-connector2")
data class TransactionStyleConfig(
    val descriptionExpression: String? = null,
)