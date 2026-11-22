package net.djvk.fireflyPlaidConnector2.smoke

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import net.djvk.fireflyPlaidConnector2.FireflyPlaidConnector2Application
import net.djvk.fireflyPlaidConnector2.sync.INITIAL_BACKFILL_COUNT_MESSAGE
import net.djvk.fireflyPlaidConnector2.sync.PlaidSyncService
import net.djvk.fireflyPlaidConnector2.sync.PolledSyncOrchestrator
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class SmokeRun(private val showDetails: Boolean) : AutoCloseable {
    val work: Path = createTempDirectory("connector-smoke-")
    private val dockerLog = work.resolve("docker.log")
    private val dockerEnvironment = listOf("SMOKE_APP_KEY", "SMOKE_DB_PASSWORD", "SMOKE_DB_ROOT_PASSWORD")
        .associateWith { randomId() }
    private val compose = listOf(
        "docker", "compose", "--env-file", work.resolve("empty.env").toString(),
        "-f", work.resolve("compose.yml").toString(), "-p", "connector-smoke-${randomId().take(12)}",
    )
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private var fireflyToken = ""
    private var stackStarted = false
    private var closed = false
    private var activeProcess: Process? = null
    private val shutdownHook = Thread({
        try {
            close()
        } catch (error: Exception) {
            System.err.println("Smoke test cleanup failed: ${error.message}")
        }
    }, "smoke-test-cleanup")

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    fun withFirefly(action: (SmokeFirefly) -> Unit) {
        work.resolve("empty.env").writeText("")
        work.resolve("compose.yml").writeText(resource("compose.yml"))
        docker(listOf("docker", "info", "--format", "{{.ServerVersion}}"), "Docker connectivity check", 15)
        println("Starting a disposable Firefly III and MySQL stack")
        synchronized(this) { stackStarted = true }
        try {
            docker(compose + listOf("up", "-d", "--quiet-pull"), "Docker Compose startup", 480)
            val address = docker(compose + listOf("port", "firefly", "8080"), "Finding the Firefly port", 15).trim()
            val port = address.substringAfterLast(':').toIntOrNull()
                ?: throw SmokeFailure("Docker Compose did not return a Firefly port.")
            val baseUrl = "http://127.0.0.1:$port"
            val readiness = HttpRequest.newBuilder(URI("$baseUrl/")).timeout(Duration.ofSeconds(2)).build()
            waitFor("Firefly HTTP startup", 120) {
                try {
                    http.send(readiness, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..399
                } catch (_: IOException) {
                    false
                }
            }

            val bootstrap = docker(
                compose + listOf("exec", "-T", "--user", "www-data", "--workdir", "/var/www/html", "firefly", "php"),
                "Firefly user bootstrap", 30, resource("bootstrap.php"),
            )
            fireflyToken = bootstrap.lineSequence().firstOrNull { it.startsWith("SMOKE_TOKEN=") }
                ?.substringAfter('=')?.takeIf(String::isNotBlank)
                ?: throw SmokeFailure("Firefly bootstrap did not return a personal access token." + diagnostic(bootstrap))
            val firefly = SmokeFirefly(http, baseUrl, fireflyToken, showDetails)
            firefly.request("GET", "/about")
            action(firefly)
        } catch (error: Exception) {
            if (showDetails) {
                runCatching { docker(compose + listOf("logs", "--no-color", "--tail", "40"), "Firefly logs", 15) }
                    .onSuccess { System.err.print(redact(it)) }
            }
            throw error
        }
    }

    fun connector(
        firefly: SmokeFirefly,
        settings: Map<String, Any?>,
        arguments: List<String> = emptyList(),
        fireflyUrl: String = firefly.baseUrl,
        expectPollFailure: Boolean = false,
    ): Int? {
        val config = work.resolve("smoke.yml")
        val polled = settings["syncMode"] == "polled"
        val configData = mutableMapOf<String, Any>("fireflyPlaidConnector2" to (settings + ("firefly" to mapOf(
            "url" to fireflyUrl, "personalAccessToken" to fireflyToken,
        ))))
        if (!showDetails) {
            val levels = mutableMapOf("net.djvk" to "WARN")
            if (polled) {
                levels[PolledSyncOrchestrator::class.java.name] = "INFO"
                levels[PlaidSyncService::class.java.name] = "INFO"
            }
            configData["logging"] = mapOf("level" to levels)
        }
        config.writeText(smokeJson.writeValueAsString(configData))

        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
            .map { Path.of(it).toAbsolutePath().toString() }
        val entryPoint = if (classpath.size == 1 && classpath.single().endsWith(".jar")) {
            listOf("-jar", classpath.single())
        } else {
            listOf("-cp", classpath.joinToString(File.pathSeparator), "${FireflyPlaidConnector2Application::class.java.name}Kt")
        }
        val log = work.resolve("connector.log")
        val builder = ProcessBuilder(listOf(java, "-Duser.timezone=${settings.getValue("timeZone")}") + entryPoint +
            "--spring.config.location=classpath:/application.yml,${config.toUri()}" + arguments)
            .directory(work.toFile())
        builder.environment().clear()
        val timeout = if (showDetails) 45L else 300L
        return try {
            withProcess(builder, log) { process ->
                if (polled) {
                    waitFor("the first poll to finish", timeout) {
                        if (!process.isAlive) {
                            val status = process.exitValue()
                            if (expectPollFailure && status != 0) return@waitFor true
                            if (!showDetails) checkLiveOutput(log.readText(), status, "polled connector")
                            throw SmokeFailure("Polled connector exited early with status $status.")
                        }
                        // The runner logs this after the Firefly writes and cursor update.
                        val finished = log.readText().lineSequence().any { it.endsWith(" : Sleeping $POLL_INTERVAL_MINUTES") }
                        if (finished && expectPollFailure) throw SmokeFailure("The poll completed after the Firefly response was lost.")
                        finished
                    }
                    val output = log.readText()
                    if (!showDetails) checkLiveOutput(output, 0, "polled connector")
                    output.lineSequence().mapNotNull {
                        it.substringAfter(" : $INITIAL_BACKFILL_COUNT_MESSAGE: ", "").toIntOrNull()
                    }.singleOrNull()
                } else {
                    val status = process.await(timeout, "Batch connector")
                    if (!showDetails) checkLiveOutput(log.readText(), status, "batch connector")
                    expect("Batch connector exit status", status, 0)
                    null
                }
            }
        } catch (error: Exception) {
            if (showDetails && Files.exists(log)) System.err.print(redact(log.readText()))
            throw error
        } finally {
            if (!showDetails) Files.deleteIfExists(log)
        }
    }

    private fun checkLiveOutput(output: String, status: Int, phase: String) {
        if (status != 0) {
            val codes = Regex(""""error_code"\s*:\s*"([A-Z][A-Z0-9_]{1,63})"""")
                .findAll(output).map { it.groupValues[1] }.toSortedSet()
            if (codes.isNotEmpty()) {
                throw SmokeFailure("Live $phase exited with status $status. Plaid returned ${codes.joinToString()}.")
            }
        }
        if ("Timeout inserting firefly tx; skipping for now" in output) {
            throw SmokeFailure("The connector skipped at least one Firefly insert after a timeout.")
        }
    }

    private fun docker(command: List<String>, label: String, seconds: Long, input: String? = null): String {
        val builder = ProcessBuilder(command)
        builder.environment().putAll(dockerEnvironment)
        return withProcess(builder, dockerLog) { process ->
            process.outputStream.bufferedWriter().use { if (input != null) it.write(input) }
            val status = process.await(seconds, label)
            val output = dockerLog.readText()
            if (status != 0) throw SmokeFailure("$label failed with status $status." + diagnostic(output))
            output
        }
    }

    private fun <T> withProcess(builder: ProcessBuilder, log: Path, action: (Process) -> T): T {
        val process = synchronized(this) {
            check(!closed) { "Smoke test is shutting down." }
            builder.redirectErrorStream(true).redirectOutput(log.toFile()).start().also { activeProcess = it }
        }
        try {
            return action(process)
        } finally {
            process.stop()
            synchronized(this) { if (activeProcess == process) activeProcess = null }
        }
    }

    override fun close() {
        val (process, removeStack) = synchronized(this) {
            if (closed) return
            closed = true
            activeProcess to stackStarted
        }
        try {
            process?.stop()
            if (removeStack) {
                val builder = ProcessBuilder(compose + listOf("down", "--volumes", "--remove-orphans"))
                    .redirectErrorStream(true).redirectOutput(dockerLog.toFile())
                builder.environment().putAll(dockerEnvironment)
                val cleanup = builder.start()
                try {
                    val status = cleanup.await(60, "Docker Compose cleanup")
                    if (status != 0) throw SmokeFailure("Docker Compose cleanup failed with status $status." + diagnostic(dockerLog.readText()))
                } finally {
                    cleanup.stop()
                }
            }
        } finally {
            try {
                if (!work.toFile().deleteRecursively()) throw SmokeFailure("Could not remove the smoke test temporary directory.")
            } finally {
                runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            }
        }
    }

    private fun redact(output: String): String = (dockerEnvironment.values + fireflyToken)
        .filter(String::isNotEmpty).fold(output) { text, secret -> text.replace(secret, "[redacted]") }

    private fun diagnostic(output: String): String = if (showDetails) "\n${redact(output).trim()}" else ""

    private fun resource(name: String): String = checkNotNull(javaClass.getResourceAsStream("/smoke/$name")) {
        "Missing smoke test resource $name."
    }.bufferedReader().use { it.readText() }
}

internal data class SmokeTransaction(val id: String, val split: JsonNode)

internal class SmokeFirefly(
    private val http: HttpClient,
    val baseUrl: String,
    private val token: String,
    private val showDetails: Boolean,
) {
    fun request(method: String, path: String, payload: Any? = null): JsonNode {
        val request = HttpRequest.newBuilder(URI("$baseUrl/api/v1$path"))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .method(method, payload?.let { HttpRequest.BodyPublishers.ofString(smokeJson.writeValueAsString(it)) }
                ?: HttpRequest.BodyPublishers.noBody())
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            val detail = if (showDetails) ": ${response.body().replace(token, "[redacted]")}" else ""
            throw SmokeFailure("Firefly $method $path returned HTTP ${response.statusCode()}$detail")
        }
        return try {
            smokeJson.readTree(response.body())
        } catch (_: JsonProcessingException) {
            throw SmokeFailure("Firefly $method $path returned invalid JSON.")
        }
    }

    fun createAccount(name: String): Int = request("POST", "/accounts", mapOf(
        "name" to name, "type" to "asset", "account_role" to "defaultAsset", "currency_code" to "USD",
    )).required("data").required("id").asText().toInt()

    fun transactions(): Map<String, SmokeTransaction> {
        val transactions = mutableMapOf<String, SmokeTransaction>()
        var page = 1
        while (true) {
            val response = request("GET", "/transactions?limit=100&page=$page")
            for (group in response.required("data")) {
                val id = group.required("id").asText()
                val splits = group.required("attributes").required("transactions")
                expect("Split count for Firefly transaction $id", splits.size(), 1)
                val split = splits[0]
                val externalId = split.path("external_id").asText("")
                if (externalId.isBlank() || externalId in transactions) {
                    throw SmokeFailure("Missing or duplicate external ID on Firefly transaction $id.")
                }
                transactions[externalId] = SmokeTransaction(id, split)
            }
            if (page >= response.required("meta").required("pagination").required("total_pages").asInt()) {
                return transactions
            }
            page++
        }
    }
}

internal fun randomId() = UUID.randomUUID().toString().replace("-", "")

private fun waitFor(description: String, seconds: Long, ready: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
    while (System.nanoTime() < deadline) {
        if (ready()) return
        Thread.sleep(200)
    }
    throw SmokeFailure("Timed out waiting for $description after ${seconds}s.")
}

private fun Process.await(seconds: Long, label: String): Int {
    if (!waitFor(seconds, TimeUnit.SECONDS)) throw SmokeFailure("$label timed out after ${seconds}s.")
    return exitValue()
}

private fun Process.stop() {
    if (!isAlive) return
    destroy()
    if (!waitFor(5, TimeUnit.SECONDS)) {
        destroyForcibly()
        waitFor(5, TimeUnit.SECONDS)
    }
}
