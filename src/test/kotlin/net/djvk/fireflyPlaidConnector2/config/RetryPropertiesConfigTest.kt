package net.djvk.fireflyPlaidConnector2.config

import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Regression test for a real production failure: [RetryPropertiesConfig]'s
 * `@ConfigurationProperties` prefixes must be kebab-case even though the actual YAML config
 * (and every other property in this app) uses camelCase under `fireflyPlaidConnector2`. A
 * camelCase prefix argument fails Spring's canonical-name validation at startup with
 * "Invalid characters: 'P', 'C'", even though the camelCase YAML keys themselves bind fine via
 * relaxed binding once the prefix argument is expressed in kebab-case.
 */
internal class RetryPropertiesConfigTest {
    // ConfigurationPropertiesAutoConfiguration registers the ConfigurationPropertiesBindingPostProcessor
    //  infrastructure that actually performs @ConfigurationProperties binding (and validates the
    //  prefix argument's canonical form) - a bare ApplicationContextRunner without it will create the
    //  bean successfully but silently skip binding entirely, masking bugs like this one.
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration::class.java))
        .withUserConfiguration(RetryPropertiesConfig::class.java)

    @Test
    fun bindsCamelCaseYamlKeysDespiteKebabCasePrefixArgument() {
        contextRunner
            .withPropertyValues(
                "fireflyPlaidConnector2.plaid.retry.maxAttempts=7",
                "fireflyPlaidConnector2.firefly.retry.maxAttempts=9",
            )
            .run { context: AssertableApplicationContext ->
                assertThat(context).hasNotFailed()

                val plaidProps = context.getBean("plaidRetryProperties", RetryProperties::class.java)
                val fireflyProps = context.getBean("fireflyRetryProperties", RetryProperties::class.java)

                assertThat(plaidProps.maxAttempts).isEqualTo(7)
                assertThat(fireflyProps.maxAttempts).isEqualTo(9)
            }
    }
}
