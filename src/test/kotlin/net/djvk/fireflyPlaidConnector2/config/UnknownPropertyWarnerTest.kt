package net.djvk.fireflyPlaidConnector2.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.core.io.ClassPathResource

internal class UnknownPropertyWarnerTest {
    private fun environment() = StandardEnvironment().apply {
        // Ignore the real process environment and system properties.
        propertySources.toList().forEach { propertySources.remove(it.name) }
    }

    @Test
    fun `shipped application yml only uses recognized keys`() {
        val env = environment()
        YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
            .forEach { env.propertySources.addLast(it) }

        assertThat(UnknownPropertyWarner.unknownKeys(env)).isEmpty()
    }

    @Test
    fun `reports typos and removed settings in any spelling`() {
        val env = environment()
        env.propertySources.addLast(
            MapPropertySource(
                "test", mapOf(
                    "fireflyPlaidConnector2.timeZone" to "UTC",
                    "fireflyPlaidConnector2.accounts[3].plaidAccountId" to "a",
                    "fireflyPlaidConnector2.plaid.retry.maxAttempts" to "2",
                    "fireflyPlaidConnector2.polled.ignorePastDataForNullCursors" to "true",
                    "firefly-plaid-connector2.categorization.old.enable" to "true",
                    "fireflyPlaidConnector2.timezone_typo" to "UTC",
                    "spring.main.banner-mode" to "off",
                )
            )
        )
        env.propertySources.addLast(
            SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, mapOf(
                    "FIREFLYPLAIDCONNECTOR2_POLLED_CURSORFILEDIRECTORYPATH" to "/opt/fpc-cursors",
                    "FIREFLYPLAIDCONNECTOR2_POLLED_CURSORPATH" to "/opt/fpc-cursors",
                )
            )
        )

        assertThat(UnknownPropertyWarner.unknownKeys(env)).containsExactlyInAnyOrder(
            "fireflyplaidconnector2.polled.ignorepastdatafornullcursors",
            "firefly-plaid-connector2.categorization.old.enable",
            "fireflyplaidconnector2.timezonetypo",
            "fireflyplaidconnector2.polled.cursorpath",
        )
    }
}
