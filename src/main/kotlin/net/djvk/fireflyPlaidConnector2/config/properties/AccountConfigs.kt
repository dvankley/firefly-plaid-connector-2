package net.djvk.fireflyPlaidConnector2.config.properties

import net.djvk.fireflyPlaidConnector2.config.AccountConfig
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "firefly-plaid-connector2")
data class AccountConfigs(
    val accounts: List<AccountConfig>
)
