package net.djvk.fireflyPlaidConnector2.smoke

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import net.djvk.fireflyPlaidConnector2.api.plaid.clientIdHeader
import net.djvk.fireflyPlaidConnector2.api.plaid.secretHeader
import net.djvk.fireflyPlaidConnector2.transactions.plaidExternalId
import net.djvk.fireflyPlaidConnector2.transactions.plaidTransferReference
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

internal class LiveSmokeAccount(val plaidAccountId: String, val accessToken: String)

internal data class LiveSmokeTransaction(val id: String, val outgoing: Boolean)

internal class LiveSmokePlaid(
    private val url: String,
    private val clientId: String,
    private val secret: String,
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun activeAccounts(accessToken: String): Set<String> =
        request("/accounts/get", accessToken).required("accounts").map { it.required("account_id").asText() }.toSet()

    fun eligibleTransactions(
        accounts: List<LiveSmokeAccount>,
        start: LocalDate,
        end: LocalDate,
    ): Map<String, List<LiveSmokeTransaction>> {
        val eligible = mutableMapOf<String, MutableList<LiveSmokeTransaction>>()
        for ((token, mappings) in accounts.groupBy { it.accessToken }) {
            val accountIds = mappings.map { it.plaidAccountId }
            var offset = 0
            do {
                val response = request("/transactions/get", token, mapOf(
                    "start_date" to start.toString(), "end_date" to end.toString(),
                    "options" to mapOf("account_ids" to accountIds, "count" to 500, "offset" to offset),
                ))
                val page = response.required("transactions")
                val total = response.required("total_transactions").asInt()
                if (page.isEmpty && offset < total) throw SmokeFailure("Plaid returned an incomplete transaction page.")
                for (transaction in page) {
                    val accountId = transaction.required("account_id").asText()
                    val amount = transaction.required("amount").asDouble()
                    if (accountId in accountIds && amount != 0.0) {
                        eligible.getOrPut(accountId) { mutableListOf() }.add(
                            LiveSmokeTransaction(transaction.required("transaction_id").asText(), amount > 0.0),
                        )
                    }
                }
                offset += page.size()
            } while (offset < total)
        }
        return eligible
    }

    private fun request(path: String, accessToken: String, fields: Map<String, Any> = emptyMap()): JsonNode {
        val request = HttpRequest.newBuilder(URI("$url$path"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header(clientIdHeader, clientId)
            .header(secretHeader, secret)
            .POST(HttpRequest.BodyPublishers.ofString(smokeJson.writeValueAsString(fields + ("access_token" to accessToken))))
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: IOException) {
            throw SmokeFailure("Plaid $path could not be reached.")
        }
        val body = try {
            smokeJson.readTree(response.body())
        } catch (_: JsonProcessingException) {
            throw SmokeFailure("Plaid $path returned invalid JSON (HTTP ${response.statusCode()}).")
        }
        if (response.statusCode() !in 200..299) {
            val code = body.path("error_code").asText().takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{1,63}")) }
            throw SmokeFailure("Plaid $path returned HTTP ${response.statusCode()}${code?.let { " $it" }.orEmpty()}.")
        }
        return body
    }
}

internal fun validateLiveSmokeMappings(accounts: List<LiveSmokeAccount>, activeAccounts: (String) -> Set<String>) {
    val problems = mutableListOf<String>()
    for ((token, mappings) in accounts.withIndex().groupBy { it.value.accessToken }) {
        val active = try {
            activeAccounts(token)
        } catch (error: SmokeFailure) {
            problems.add("Mappings ${mappings.joinToString { "#${it.index + 1}" }}: ${error.message}")
            continue
        }
        for ((index, mapping) in mappings) {
            if (mapping.plaidAccountId !in active) problems.add("Mapping #${index + 1}: account is absent from its Plaid Item.")
        }
    }
    if (problems.isNotEmpty()) throw SmokeFailure("Plaid mapping preflight failed.\n${problems.joinToString("\n")}")
}

internal fun reportLiveSmokeCoverage(
    phase: String,
    accounts: List<LiveSmokeAccount>,
    fireflyAccountIds: List<Int>,
    eligible: Map<String, List<LiveSmokeTransaction>>,
    writes: Map<String, SmokeTransaction>,
    report: (String) -> Unit = ::println,
): Boolean {
    val transfers = writes.values.groupBy { it.split.path("internal_reference").asText() }
    var complete = true
    val missingWrites = mutableListOf<Int>()
    for ((index, account) in accounts.withIndex()) {
        val expected = eligible[account.plaidAccountId].orEmpty()
        val fireflyAccountId = fireflyAccountIds[index].toString()
        val matched = expected.count { transaction ->
            val direct = writes[plaidExternalId(transaction.id)]
            val transferLegs = transfers[plaidTransferReference(transaction.id)].orEmpty()
            val accountSide = if (transaction.outgoing) "source_id" else "destination_id"
            (listOfNotNull(direct) + transferLegs).any { it.split.path(accountSide).asText() == fireflyAccountId }
        }
        val status = when {
            expected.isEmpty() -> {
                complete = false
                "inconclusive (no eligible activity)"
            }
            matched != expected.size -> {
                missingWrites.add(index + 1)
                "missing expected writes"
            }
            else -> "verified"
        }
        report("$phase mapping #${index + 1}: ${expected.size} eligible, $matched matched Firefly writes, $status.")
    }
    if (missingWrites.isNotEmpty()) {
        throw SmokeFailure("$phase is missing expected Firefly writes for ${missingWrites.joinToString { "mapping #$it" }}.")
    }
    return complete
}
