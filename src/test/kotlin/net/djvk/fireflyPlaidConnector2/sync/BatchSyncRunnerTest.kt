package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsGetRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsGetResponse
import net.djvk.fireflyPlaidConnector2.config.AccountConfig
import net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs
import net.djvk.fireflyPlaidConnector2.config.properties.TransactionStyleConfig
import net.djvk.fireflyPlaidConnector2.lib.*
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate

internal class BatchSyncRunnerTest {
    @Test
    fun `batch request counts its lookback from the inclusive end date`() = runBlocking<Unit> {
        val endDate = LocalDate.of(2025, 1, 10)
        val plaid = PlaidMock()
        val runner = createRunner(plaid, FireflyMock(), syncDays = 14, batchEndDate = endDate)
        val response = createPlaidResponse(
            TransactionsGetResponse(emptyList(), emptyList(), 0, PlaidFixtures.getItem(), "request"),
        )
        whenever(plaid.api.transactionsGet(any())).thenReturn(response)

        runner.run()

        val request = argumentCaptor<TransactionsGetRequest>()
        verify(plaid.api).transactionsGet(request.capture())
        assertThat(request.firstValue.startDate).isEqualTo(endDate.minusDays(14))
        assertThat(request.firstValue.endDate).isEqualTo(endDate)
    }

    companion object {
        fun createRunner(
            plaid: PlaidMock,
            firefly: FireflyMock,
            syncDays: Int = 200,
            setInitialBalance: Boolean = false,
            plaidBatchSize: Int = 100,
            syncHelper: SyncHelper? = null,
            converter: TransactionConverter? = null,
            batchEndDate: LocalDate? = null,
        ): BatchSyncRunner {
            val defaultSyncHelper = SyncHelper(
                plaidAccountsConfig = AccountConfigs(listOf(AccountConfig(1, "account1Token", "plaidAccount1"))),
                fireflyAccessToken = "testToken",
                fireflyAboutApi = firefly.aboutApi,
                fireflyTxApi = firefly.transactionsApi,
                fireflyAccountsApi = firefly.accountsApi,
                fireflyApiWrapper = firefly.wrapper,
                fireflySearchApi = mock(),
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
                batchEndDate,
            )
        }

    }
}
