package net.djvk.fireflyPlaidConnector2.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "firefly-plaid-connector2.plaid")
data class PlaidTransactionProperties(val batchSize: Int) {
    init {
        require(batchSize in 1..500) {
            "fireflyPlaidConnector2.plaid.batchSize must be between 1 and 500"
        }
    }
}
