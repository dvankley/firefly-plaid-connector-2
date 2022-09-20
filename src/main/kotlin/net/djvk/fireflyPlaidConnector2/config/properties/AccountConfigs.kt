package net.djvk.fireflyPlaidConnector2.config.properties

import net.djvk.fireflyPlaidConnector2.config.AccountConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "firefly-plaid-connector2")
@ConstructorBinding
data class AccountConfigs(
    val accounts: List<AccountConfig>
)
