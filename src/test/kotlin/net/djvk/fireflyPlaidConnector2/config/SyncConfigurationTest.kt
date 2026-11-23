package net.djvk.fireflyPlaidConnector2.config

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import net.djvk.fireflyPlaidConnector2.FireflyPlaidConnector2Application
import net.djvk.fireflyPlaidConnector2.config.properties.PlaidTransactionProperties
import net.djvk.fireflyPlaidConnector2.sync.BatchSyncRunner
import net.djvk.fireflyPlaidConnector2.sync.CursorManager
import net.djvk.fireflyPlaidConnector2.sync.PolledSyncOrchestrator
import net.djvk.fireflyPlaidConnector2.sync.Runner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.boot.Banner
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.SimpleCommandLinePropertySource
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.function.Supplier
import kotlin.test.assertFailsWith

@ExtendWith(OutputCaptureExtension::class)
internal class SyncConfigurationTest {
    @TempDir
    lateinit var directory: Path

    private val prefix = "fireflyPlaidConnector2"
    private val firstToken = "first-test-access-token"
    private val secondToken = "second-test-access-token"
    private val commonSettings = listOf(
        "timeZone=UTC",
        "transferMatchWindowDays=3",
        "firefly.url=http://firefly.test",
        "firefly.personalAccessToken=test-firefly-token",
        "plaid.url=http://plaid.test",
        "plaid.clientId=test-client",
        "plaid.secret=test-secret",
        "plaid.batchSize=100",
        "accounts[0].plaidAccountId=account-a",
        "accounts[0].plaidItemAccessToken=$firstToken",
        "accounts[0].fireflyAccountId=1",
    )
    private val batchSettings = listOf("syncMode=batch", "batch.maxSyncDays=5")
    private val polledSettings = listOf(
        "syncMode=polled",
        "polled.syncFrequencyMinutes=10",
        "polled.existingFireflyPullWindowDays=5",
        "polled.cursorFileDirectoryPath=persistence",
    )
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(FireflyPlaidConnector2Application::class.java)

    @ParameterizedTest
    @CsvSource(
        "batch, plaid.batchSize=0, plaid.batchSize must be between 1 and 500",
        "polled, plaid.batchSize=-1, plaid.batchSize must be between 1 and 500",
        "batch, plaid.batchSize=501, plaid.batchSize must be between 1 and 500",
        "batch, batch.maxSyncDays=-1, batch.maxSyncDays must be nonnegative",
        "polled, polled.syncFrequencyMinutes=0, polled.syncFrequencyMinutes must be positive",
        "polled, polled.syncFrequencyMinutes=-1, polled.syncFrequencyMinutes must be positive",
        "polled, polled.existingFireflyPullWindowDays=-1, polled.existingFireflyPullWindowDays must be nonnegative",
        "polled, polled.existingFireflyPullWindowDays=3, polled.existingFireflyPullWindowDays must exceed transferMatchWindowDays",
        "polled, polled.initialBackfillThrough=2025-01-10, polled.initialBackfillThrough requires initialBackfillAfter",
    )
    fun `invalid settings fail to bind`(mode: String, setting: String, reason: String) {
        contextRunner.withPropertyValues(*properties(settingsFor(mode) + setting)).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasRootCauseMessage("$prefix.$reason")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["2025-01-09", "2025-01-10"])
    fun `the backfill end must be later than the exclusive start`(through: String) {
        val settings = polledSettings + listOf(
            "polled.initialBackfillAfter=2025-01-10",
            "polled.initialBackfillThrough=$through",
        )
        contextRunner.withPropertyValues(*properties(settings)).run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasRootCauseMessage(
                "$prefix.polled.initialBackfillThrough must be later than initialBackfillAfter",
            )
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 500])
    fun `batch accepts the Plaid page size limits without polled settings`(size: Int) {
        contextRunner.withPropertyValues(*properties(batchSettings + "plaid.batchSize=$size")).run { context ->
            assertThat(context).hasSingleBean(Runner::class.java)
            assertThat(context.getBean(Runner::class.java)).isInstanceOf(BatchSyncRunner::class.java)
            assertThat(context.getBean(PlaidTransactionProperties::class.java).batchSize).isEqualTo(size)
            assertThat(context.getBean(BatchSyncProperties::class.java).endDate).isNull()
            assertThat(context).doesNotHaveBean(CursorManager::class.java)
        }
    }

    @Test
    fun `batch binds camelCase YAML and command line overrides despite invalid polled settings`() {
        val yaml = Files.writeString(directory.resolve("application.yml"), """
            fireflyPlaidConnector2:
              syncMode: batch
              batch:
                maxSyncDays: 5
                endDate: 2025-01-11
                setInitialBalance: true
                balanceMinLastUpdatedDatetimeSeconds: 123
              polled:
                initialBackfillAfter: invalid
        """.trimIndent())
        contextRunner
            .withPropertyValues(*properties(emptyList()), "spring.config.location=${yaml.toUri()}")
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withInitializer { context ->
                context.environment.propertySources.addFirst(
                    SimpleCommandLinePropertySource("--$prefix.batch.maxSyncDays=0"),
                )
            }
            .run { context ->
                assertThat(context).hasSingleBean(Runner::class.java)
                assertThat(context.getBean(Runner::class.java)).isInstanceOf(BatchSyncRunner::class.java)
                assertThat(context.getBean(BatchSyncProperties::class.java)).isEqualTo(
                    BatchSyncProperties(0, LocalDate.of(2025, 1, 11), true, 123),
                )
                assertThat(context).doesNotHaveBean(PolledSyncProperties::class.java)
            }
    }

    @ParameterizedTest
    @CsvSource(
        "<omitted>, <omitted>",
        "'', ''",
        "2025-01-10, <omitted>",
        "2025-01-10, 2025-01-11",
        nullValues = ["<omitted>"],
    )
    fun `polled binds valid date settings and uses the Docker cursor path`(after: String?, through: String?) {
        val settings = polledSettings.filterNot { it.startsWith("polled.cursorFileDirectoryPath=") } + listOf(
            "batch.maxSyncDays=invalid",
            "polled.existingFireflyPullWindowDays=4",
        ) + listOfNotNull(
            after?.let { "polled.initialBackfillAfter=$it" },
            through?.let { "polled.initialBackfillThrough=$it" },
        )
        contextRunner
            .withPropertyValues(*properties(settings))
            .withInitializer { context ->
                context.environment.propertySources.addFirst(
                    SystemEnvironmentPropertySource(
                        "docker-systemEnvironment",
                        mapOf("FIREFLYPLAIDCONNECTOR2_POLLED_CURSORFILEDIRECTORYPATH" to directory.toString()),
                    ),
                )
            }
            .run { context ->
                assertThat(context).hasSingleBean(Runner::class.java)
                assertThat(context.getBean(Runner::class.java)).isInstanceOf(PolledSyncOrchestrator::class.java)
                assertThat(context).doesNotHaveBean(BatchSyncProperties::class.java)
                assertThat(context.getBean(CursorManager::class.java).cursorFilePath)
                    .isEqualTo(directory.resolve("plaid_sync_cursors.txt"))
                val dates = context.getBean(PolledSyncProperties::class.java)
                assertThat(dates.initialBackfillAfter).isEqualTo(after?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
                assertThat(dates.initialBackfillThrough).isEqualTo(through?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
            }
    }

    @ParameterizedTest
    @CsvSource("batch, same", "batch, different", "polled, same", "polled, different")
    fun `duplicate account IDs fail startup without exposing access tokens`(mode: String, item: String, output: CapturedOutput) {
        val settings = settingsFor(mode) + listOf(
            "accounts[1].plaidAccountId=account-a",
            "accounts[1].plaidItemAccessToken=${if (item == "same") firstToken else secondToken}",
            "accounts[1].fireflyAccountId=${if (item == "same") 1 else 2}",
        )
        val reason = "$prefix.accounts[1].plaidAccountId duplicates accounts[0].plaidAccountId"
        val failure = assertStartupFailureBeforeRequests(settings, reason)
        assertThat(failure.stackTraceToString()).doesNotContain(firstToken, secondToken)
        assertThat(output.all).contains(reason).doesNotContain(firstToken, secondToken)
    }

    @ParameterizedTest
    @CsvSource(
        "batch, batch.maxSyncDays=-1, batch.maxSyncDays must be nonnegative",
        "polled, polled.syncFrequencyMinutes=0, polled.syncFrequencyMinutes must be positive",
    )
    fun `invalid settings for the selected mode fail startup before either API is called`(
        mode: String,
        setting: String,
        reason: String,
    ) {
        assertStartupFailureBeforeRequests(settingsFor(mode) + setting, "$prefix.$reason")
    }

    private fun assertStartupFailureBeforeRequests(settings: List<String>, reason: String): Exception =
        MockEngine { respond("Unexpected API request during startup", HttpStatusCode.BadRequest) }.use { engine ->
            val failure = assertFailsWith<Exception> {
                SpringApplicationBuilder(FireflyPlaidConnector2Application::class.java)
                    .bannerMode(Banner.Mode.OFF)
                    .initializers(ApplicationContextInitializer<GenericApplicationContext> { context ->
                        context.registerBean(
                            "startupTestHttpEngine", HttpClientEngine::class.java, Supplier { engine },
                            BeanDefinitionCustomizer { it.isPrimary = true },
                        )
                    })
                    .run(
                        "--spring.config.location=optional:classpath:/no-sync-configuration.yml",
                        *properties(settings).map { "--$it" }.toTypedArray(),
                    ).close()
            }

            assertThat(engine.requestHistory).isEmpty()
            assertThat(failure).hasRootCauseMessage(reason)
            failure
        }

    private fun settingsFor(mode: String) = if (mode == "batch") batchSettings else polledSettings

    private fun properties(settings: List<String>) =
        (commonSettings + settings)
            .associateBy { it.substringBefore('=') }
            .values.map { "$prefix.$it" }.toTypedArray()
}
