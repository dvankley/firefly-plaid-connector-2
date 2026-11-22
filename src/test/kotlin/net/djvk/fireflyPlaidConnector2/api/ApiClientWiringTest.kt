package net.djvk.fireflyPlaidConnector2.api

import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AboutApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AccountsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AutocompleteApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper
import net.djvk.fireflyPlaidConnector2.config.RetryPropertiesConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Verifies that splitting the shared Ktor `httpClientConfig` bean into Plaid- and Firefly-specific
 * qualified beans (see [ApiConfiguration]) doesn't break Spring's autowiring for either the
 * explicitly-qualified consumers or the many unqualified/unused generated Firefly API client
 * classes (e.g. [AutocompleteApi]), which must still resolve unambiguously via the [Primary][org.springframework.context.annotation.Primary]
 * bean.
 */
internal class ApiClientWiringTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(
            ApiConfiguration::class.java,
            RetryPropertiesConfig::class.java,
            PlaidApiWrapper::class.java,
            FireflyApiWrapper::class.java,
            TransactionsApi::class.java,
            AccountsApi::class.java,
            AboutApi::class.java,
            AutocompleteApi::class.java,
        )
        .withPropertyValues(
            "fireflyPlaidConnector2.plaid.url=https://plaid.test",
            "fireflyPlaidConnector2.plaid.clientId=testClientId",
            "fireflyPlaidConnector2.plaid.secret=testSecret",
            "fireflyPlaidConnector2.firefly.url=https://firefly.test",
        )

    @Test
    fun contextLoadsWithoutAmbiguousBeanErrors() {
        contextRunner.run { context: AssertableApplicationContext ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(PlaidApiWrapper::class.java)
            assertThat(context).hasSingleBean(FireflyApiWrapper::class.java)
            assertThat(context).hasSingleBean(TransactionsApi::class.java)
            assertThat(context).hasSingleBean(AutocompleteApi::class.java)
        }
    }
}
