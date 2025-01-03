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

        @JvmStatic
        fun provideDaysOfHistoryCases(): List<Arguments> {
            return listOf(
                Arguments.of("minimum of 180 (1 -> 180)", 1, 180),
                Arguments.of("maximum of 730 (730 -> 730)", 730, 730),
                Arguments.of("maximum of 730 (999 -> 730)", 9999, 730),
                Arguments.of("syncDays plus one (180 -> 181)", 180, 181),
                Arguments.of("syncDays plus one (500 -> 501)", 500, 501),
            )
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideDaysOfHistoryCases")
    fun runRequestsExpectedDaysOfHistory(
        testName: String,
        configuredSyncDays: Int,
        expectedDaysRequested: Int,
    ) {
        val firefly = FireflyMock()
        val plaid = PlaidMock()

        val runner = createRunner(plaid, firefly, syncDays = configuredSyncDays)
        val response = TransactionsGetResponse(listOf(), listOf(), 0, PlaidFixtures.getItem(), "requestId1")

        plaid.api.stub {
            onBlocking { transactionsGet(any()) } doAnswer { createPlaidResponse(response) }
        }

        runBlocking {
            runner.run()
        }

        verifyBlocking(plaid.api) {
            transactionsGet(check { actual ->
                assertThat(actual).extracting { it.options }
                    .isNotNull()
                assertThat(actual.options!!).extracting { it.daysRequested }.isEqualTo(expectedDaysRequested)
            })
        }
    }
}