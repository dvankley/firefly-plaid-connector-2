package net.djvk.fireflyPlaidConnector2.config.properties

import net.djvk.fireflyPlaidConnector2.config.AccountConfig
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "firefly-plaid-connector2")
data class AccountConfigs(
    val accounts: List<AccountConfig>
) {
    init {
        val firstIndexByPlaidId = mutableMapOf<String, Int>()
        accounts.forEachIndexed { index, account ->
            val firstIndex = firstIndexByPlaidId.putIfAbsent(account.plaidAccountId, index)
            require(firstIndex == null) {
                "fireflyPlaidConnector2.accounts[$index].plaidAccountId duplicates accounts[$firstIndex].plaidAccountId"
            }
        }
    }
}
