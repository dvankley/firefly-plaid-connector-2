package net.djvk.fireflyPlaidConnector2.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Spring silently ignores configuration keys that nothing binds, so a typo or a setting removed in a newer version
 * would otherwise have no effect and no warning. This logs every `fireflyPlaidConnector2.*` key that the connector
 * doesn't recognize.
 *
 * When adding a setting, add its key to [knownKeys].
 */
@Component
class UnknownPropertyWarner(environment: Environment) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        for (key in unknownKeys(environment)) {
            logger.warn(
                "Ignoring unrecognized configuration property {}. Check it for typos, or remove it if it " +
                        "was a setting from an older version.",
                key,
            )
        }
    }

    companion object {
        private const val ROOT = "firefly-plaid-connector2"

        private val retryKeys = listOf("max-attempts", "initial-delay-ms", "max-delay-ms", "multiplier", "jitter-factor")

        /** Every setting the connector reads, relative to [ROOT], in canonical kebab-case form. */
        internal val knownKeys: List<String> = listOf(
            "sync-mode",
            "use-name-for-destination",
            "time-zone",
            "transfer-match-window-days",
            "description-expression",
            "polled.sync-frequency-minutes",
            "polled.existing-firefly-pull-window-days",
            "polled.cursor-file-directory-path",
            "polled.allow-item-to-fail",
            "polled.initial-backfill-after",
            "polled.initial-backfill-through",
            "batch.max-sync-days",
            "batch.end-date",
            "batch.set-initial-balance",
            "batch.balance-min-last-updated-datetime-seconds",
            "categorization.primary.enable",
            "categorization.primary.prefix",
            "categorization.detailed.enable",
            "categorization.detailed.prefix",
            "plaid.url",
            "plaid.client-id",
            "plaid.secret",
            "plaid.batch-size",
            "plaid.max-retries",
            "firefly.url",
            "firefly.personal-access-token",
            "firefly.request-timeout-millis",
            "accounts[0].firefly-account-id",
            "accounts[0].plaid-item-access-token",
            "accounts[0].plaid-account-id",
        ) + listOf("plaid", "firefly").flatMap { api -> retryKeys.map { "$api.retry.$it" } }

        /**
         * Known keys plus their parents, since YAML reports a section whose children are all commented out
         * (e.g. an empty `accounts:`) as a key of its own.
         */
        private val knownNormalized: Set<String> = knownKeys
            .map { ConfigurationPropertyName.of("$ROOT.$it") }
            .flatMap { name -> (1..name.numberOfElements).map { normalize(name.chop(it)) } }
            .toSet()

        private val rootNormalized = normalize(ConfigurationPropertyName.of(ROOT))

        fun unknownKeys(environment: Environment): Set<String> {
            return ConfigurationPropertySources.get(environment)
                .filterIsInstance<IterableConfigurationPropertySource>()
                .flatMap { it.toList() }
                .filter { name -> name.numberOfElements > 0 && normalize(name.chop(1)) == rootNormalized }
                .filter { normalize(it) !in knownNormalized }
                .map { it.toString() }
                .toSortedSet()
        }

        /**
         * Reduces a name to a form that ignores case, dashes and list indexes, so YAML (`fireflyPlaidConnector2`),
         * kebab-case and environment variable (`FIREFLYPLAIDCONNECTOR2_...`) spellings of a key all match.
         */
        private fun normalize(name: ConfigurationPropertyName): String {
            return (0 until name.numberOfElements).joinToString(".") { i ->
                if (name.isNumericIndex(i)) "[]" else name.getElement(i, ConfigurationPropertyName.Form.UNIFORM)
            }
        }
    }
}
