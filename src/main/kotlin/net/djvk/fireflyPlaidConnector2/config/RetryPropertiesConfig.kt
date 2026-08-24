package net.djvk.fireflyPlaidConnector2.config

import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Binds [RetryProperties] twice, at two different config prefixes, so Plaid and Firefly retry
 * behavior can be tuned independently while sharing the same knob names.
 *
 * The prefix argument must be kebab-case (matching [AccountConfigs][net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs]'s
 * existing "firefly-plaid-connector2" prefix) even though the YAML itself uses camelCase
 * ("fireflyPlaidConnector2") - relaxed binding still matches it correctly, but
 * `@ConfigurationProperties` validates the *prefix argument itself* against Spring's stricter
 * canonical-name rules, which reject camelCase.
 */
@Configuration
class RetryPropertiesConfig {
    @Bean
    @ConfigurationProperties(prefix = "firefly-plaid-connector2.plaid.retry")
    fun plaidRetryProperties(): RetryProperties = RetryProperties()

    @Bean
    @ConfigurationProperties(prefix = "firefly-plaid-connector2.firefly.retry")
    fun fireflyRetryProperties(): RetryProperties = RetryProperties()
}
