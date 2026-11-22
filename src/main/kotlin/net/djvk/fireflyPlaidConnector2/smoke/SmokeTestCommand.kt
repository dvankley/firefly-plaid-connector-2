package net.djvk.fireflyPlaidConnector2.smoke

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.sync.CursorManager
import org.springframework.boot.DefaultApplicationArguments
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.MarkedYAMLException
import org.yaml.snakeyaml.error.YAMLException
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.io.path.inputStream

internal val smokeJson = jacksonObjectMapper()
internal const val POLL_INTERVAL_MINUTES = 60

internal class SmokeFailure(message: String) : RuntimeException(message)

internal fun expect(label: String, actual: Any?, expected: Any?) {
    if (actual != expected) {
        throw SmokeFailure("$label\n  expected $expected\n  observed $actual")
    }
}

internal object SmokeTestCommand {
    private val liveOptions = setOf("profile", "batch-lookback-days", "polled-lookback-days")

    fun run(arguments: Array<String>): Int {
        return try {
            val args = DefaultApplicationArguments(*arguments)
            val supportedOptions = liveOptions + setOf("help", "live")
            require(args.nonOptionArgs.isEmpty() && supportedOptions.containsAll(args.optionNames)) {
                "Unknown smoke-test arguments. Run smoke-test --help for usage."
            }
            if (args.containsOption("help")) {
                println("""
                    Usage: smoke-test [--live --profile=PATH --batch-lookback-days=N --polled-lookback-days=N]

                    Run a smoke test against a disposable local Firefly III and MySQL stack.
                    --live                    Run batch and polled checks against the Plaid API.
                    --profile=PATH            Required Spring YAML profile for a live run.
                    --batch-lookback-days=N   Required days before today to start the batch.
                    --polled-lookback-days=N  Required days before today to end the batch and set the poll cutoff.
                """.trimIndent())
                return 0
            }

            val live = args.containsOption("live")
            require(!live || args.getOptionValues("live").isEmpty()) { "Use --profile=PATH to select the live profile." }
            val settings = if (live) {
                val path = requireNotNull(optionValue(args, "profile")) { "--live requires --profile=PATH." }
                val batchLookback = requiredLookback(args, "batch-lookback-days")
                val polledLookback = requiredLookback(args, "polled-lookback-days")
                require(batchLookback >= polledLookback) {
                    "--batch-lookback-days must be at least --polled-lookback-days."
                }
                readLiveProfile(Path.of(path), batchLookback, polledLookback)
            } else {
                require(liveOptions.none(args::containsOption)) { "Profile and lookback arguments require --live." }
                null
            }

            SmokeRun(showDetails = !live).use { run ->
                run.withFirefly { firefly ->
                    firefly.request("POST", "/currencies/USD/primary")
                    expect("Transaction count in fresh Firefly", firefly.transactions().size, 0)
                    if (settings == null) exerciseSynthetic(run, firefly) else exerciseLive(run, firefly, settings)
                }
            }
            println("Smoke test passed.")
            0
        } catch (error: Exception) {
            System.err.println("Smoke test failed: ${error.message}")
            error.suppressed.forEach { System.err.println("Cleanup failed: ${it.message}") }
            1
        }
    }

    private fun optionValue(args: DefaultApplicationArguments, name: String): String? {
        if (!args.containsOption(name)) return null
        return args.getOptionValues(name).singleOrNull()?.takeIf(String::isNotBlank)
            ?: throw SmokeFailure("Supply a single value for --$name.")
    }

    private fun requiredLookback(args: DefaultApplicationArguments, name: String): Int {
        val value = requireNotNull(optionValue(args, name)) { "--live requires --$name=N." }
        return value.toIntOrNull()?.takeIf { it > 0 }
            ?: throw SmokeFailure("--$name must be a positive integer.")
    }
}

private data class LiveProfile(
    val accounts: List<Map<String, String>>,
    val batchStart: LocalDate,
    val cutoff: LocalDate,
    val polledEnd: LocalDate,
    val polledSettings: Map<String, Any>,
    val connectorSettings: Map<String, Any?>,
)

private fun readLiveProfile(path: Path, batchLookback: Int, polledLookback: Int): LiveProfile {
    val document = try {
        path.inputStream().use { Yaml(SafeConstructor(LoaderOptions())).load<Any?>(it) } as? Map<*, *>
            ?: throw SmokeFailure("The live profile must contain a YAML mapping.")
    } catch (error: MarkedYAMLException) {
        val location = error.problemMark?.let { " near line ${it.line + 1}" }.orEmpty()
        throw SmokeFailure("Invalid YAML in the live profile$location.")
    } catch (_: YAMLException) {
        throw SmokeFailure("Invalid YAML in the live profile.")
    }

    fun section(parent: Map<*, *>, key: String): Map<*, *> {
        if (key !in parent) return emptyMap<String, Any>()
        return parent[key] as? Map<*, *> ?: throw SmokeFailure("Live profile field $key must be a mapping.")
    }

    val envVariable = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")
    fun requiredString(parent: Map<*, *>, key: String, location: String): String {
        val raw = parent[key] as? String
        val variable = raw?.let(envVariable::matchEntire)
        val value = if (variable == null) raw else System.getenv(variable.groupValues[1])
        return value?.takeIf { it.isNotBlank() && "\${" !in it }
            ?: throw SmokeFailure("Live profile requires a value for $location.$key.")
    }

    val profile = section(document, "fireflyPlaidConnector2")
    val plaid = section(profile, "plaid")
    val url = if ("url" in plaid) requiredString(plaid, "url", "plaid").trimEnd('/') else "https://production.plaid.com"
    require(url in setOf("https://production.plaid.com", "https://sandbox.plaid.com")) {
        "Live mode expects an official Plaid Production or Sandbox HTTPS URL."
    }
    val plaidSettings = mutableMapOf<String, Any>("url" to url)
    for (key in listOf("clientId", "secret")) {
        plaidSettings[key] = requiredString(plaid, key, "plaid")
    }
    for (key in listOf("batchSize", "maxRetries")) {
        if (key in plaid) plaidSettings[key] = positiveInt(plaid[key], "plaid.$key")
    }

    val configuredAccounts = (profile["accounts"] as? List<*>)?.takeIf { it.isNotEmpty() }
        ?: throw SmokeFailure("Live profile requires at least one account under fireflyPlaidConnector2.accounts.")
    val accounts = configuredAccounts.mapIndexed { index, entry ->
        val label = "account ${index + 1}"
        val account = entry as? Map<*, *> ?: throw SmokeFailure("Live profile $label must be a mapping.")
        listOf("plaidAccountId", "plaidItemAccessToken").associateWith { requiredString(account, it, label) }
    }
    require(accounts.map { it.getValue("plaidAccountId") }.toSet().size == accounts.size) {
        "Live profile contains duplicate Plaid account IDs."
    }

    val polled = section(profile, "polled")["existingFireflyPullWindowDays"]
        ?.let { mapOf("existingFireflyPullWindowDays" to it) }.orEmpty()
    val timeZone = profile["timeZone"]?.toString() ?: "America/New_York"
    val today = LocalDate.now(ZoneId.of(timeZone))
    val settings = listOf("categorization", "descriptionExpression", "transferMatchWindowDays", "useNameForDestination")
        .filter { it in profile }.associateWith { profile[it] } +
        mapOf("timeZone" to timeZone, "plaid" to plaidSettings)
    return LiveProfile(
        accounts = accounts,
        batchStart = today.minusDays(batchLookback.toLong()),
        cutoff = today.minusDays(polledLookback.toLong()),
        polledEnd = today,
        polledSettings = polled,
        connectorSettings = settings,
    )
}

private fun positiveInt(value: Any?, field: String): Int = (value as? Int)?.takeIf { it > 0 }
    ?: throw SmokeFailure("Live profile field $field must be a positive integer.")

private fun exerciseLive(run: SmokeRun, firefly: SmokeFirefly, profile: LiveProfile) {
    val cutoff = profile.cutoff
    val accounts = profile.accounts.mapIndexed { index, account ->
        account + ("fireflyAccountId" to firefly.createAccount("Smoke Asset ${index + 1}"))
    }
    val settings = profile.connectorSettings + ("accounts" to accounts)
    val batchSettings = settings + mapOf("syncMode" to "batch", "batch" to mapOf("setInitialBalance" to false))
    // The batch runner measures its lookback from its end date.
    val batchArguments = listOf(
        "--fireflyPlaidConnector2.batch.endDate=$cutoff",
        "--fireflyPlaidConnector2.batch.maxSyncDays=${ChronoUnit.DAYS.between(profile.batchStart, cutoff)}",
    )
    println("Live Plaid batch import for ${accounts.size} account(s) from ${profile.batchStart} through $cutoff")
    run.connector(firefly, batchSettings, batchArguments)
    val imported = firefly.transactions()
    if (imported.isEmpty()) {
        throw SmokeFailure("No transactions were imported. Increase --batch-lookback-days to widen the batch window.")
    }
    println("Imported ${imported.size} transaction(s). Rerunning the live batch.")
    run.connector(firefly, batchSettings, batchArguments)
    val after = firefly.transactions()
    val changed = imported.count { (id, transaction) -> after[id] != transaction }
    if (changed > 0) throw SmokeFailure("The rerun changed or removed $changed previously imported transaction(s).")
    println("Rerun retained all ${imported.size} original transaction(s) with no duplicates. " +
        "New transactions on rerun: ${after.size - imported.size}.")

    val polled = profile.polledSettings + mapOf(
        "syncFrequencyMinutes" to POLL_INTERVAL_MINUTES,
        "cursorFileDirectoryPath" to run.work.toString(),
        "allowItemToFail" to false,
    )
    val polledArguments = listOf(
        "--fireflyPlaidConnector2.polled.initialBackfillAfter=$cutoff",
        "--fireflyPlaidConnector2.polled.initialBackfillThrough=${profile.polledEnd}",
    )
    val itemTokens = profile.accounts.map { it.getValue("plaidItemAccessToken") }.toSet()
    println("Live Plaid first poll for ${itemTokens.size} item(s), importing after $cutoff through ${profile.polledEnd}")
    val eligible = run.connector(firefly, settings + mapOf("syncMode" to "polled", "polled" to polled), polledArguments)
        ?: throw SmokeFailure("The first poll did not report its backfill transaction count.")
    val cursors = runBlocking { CursorManager(run.work.toString()).readCursorMap() }
    if (cursors.keys != itemTokens || cursors.values.any(String::isBlank)) {
        throw SmokeFailure("The first poll did not save a nonempty cursor for every Plaid item.")
    }

    val afterPoll = firefly.transactions()
    val added = afterPoll.keys.count { it !in after }
    val removed = after.keys.count { it !in afterPoll }
    val updated = afterPoll.count { (id, transaction) -> id in after && after[id] != transaction }
    println("First poll saved cursors for all ${itemTokens.size} item(s). " +
        "Firefly transactions: $added added, $updated updated, $removed removed.")
    if (eligible == 0) {
        println("Polled write check inconclusive. Plaid returned no nonzero transactions in the polled window.")
    } else if (added == 0 && updated == 0) {
        throw SmokeFailure("The poll wrote no Firefly rows for $eligible eligible Plaid transactions.")
    }
}
