package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsGetResponse
import net.djvk.fireflyPlaidConnector2.config.AccountConfig
import net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs
import net.djvk.fireflyPlaidConnector2.config.properties.TransactionStyleConfig
import net.djvk.fireflyPlaidConnector2.lib.*
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.*

internal class BatchSyncRunnerTest {
    companion object {
        fun createRunner(
            plaid: PlaidMock,
            firefly: FireflyMock,
            syncDays: Int = 200,
            setInitialBalance: Boolean = false,
            plaidBatchSize: Int = 100,
            syncHelper: SyncHelper? = null,
            converter: TransactionConverter? = null,
        ): BatchSyncRunner {
            val defaultSyncHelper = SyncHelper(
                plaidAccountsConfig = AccountConfigs(listOf(AccountConfig(1, "account1Token", "plaidAccount1"))),
                fireflyAccessToken = "testToken",
                fireflyAboutApi = firefly.aboutApi,
                fireflyTxApi = firefly.transactionsApi,
                fireflyAccountsApi = firefly.accountsApi,
            )
            val defaultTransactionConverter = TransactionConverter(
                useNameForDestination = false,
                timeZoneString = "America/New_York",
                transferMatchWindowDays = 5,
                enablePrimaryCategorization = true,
                primaryCategoryPrefix = "primary-",
                enableDetailedCategorization = true,
                detailedCategoryPrefix = "detailed-",
                txStyle = TransactionStyleConfig(),
            )

            return BatchSyncRunner(
                syncDays,
                setInitialBalance,
                null,
                plaidBatchSize,
                plaid.wrapper,
                syncHelper ?: defaultSyncHelper,
                firefly.accountsApi,
                converter ?: defaultTransactionConverter,
            )
        }

    }
}