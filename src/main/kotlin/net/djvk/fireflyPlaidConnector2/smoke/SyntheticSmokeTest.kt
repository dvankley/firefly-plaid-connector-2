package net.djvk.fireflyPlaidConnector2.smoke

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlin.io.path.readText

private val plaidAccountIds = listOf("aaa", "bbb")

private data class PlaidExchange(val path: String, val options: Map<String, Any?>, val response: Map<String, Any?>)

private val plaidAccounts = plaidAccountIds.zip(listOf("checking", "savings")).map { (id, subtype) ->
    mapOf("account_id" to id, "name" to "Smoke $subtype", "type" to "depository", "subtype" to subtype,
        "balances" to mapOf("current" to 100, "iso_currency_code" to "USD"))
}

internal fun exerciseSynthetic(run: SmokeRun, firefly: SmokeFirefly) {
    val plaidToken = "access-sandbox-${randomId()}"
    val plaidClientId = randomId()
    val plaidSecret = randomId()
    val checking = firefly.createAccount("Smoke Checking")
    val savings = firefly.createAccount("Smoke Savings")
    val date = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()
    fun transaction(id: String, amount: Number, name: String, primary: String, detailed: String, account: String = "aaa") = mapOf(
        "account_id" to account, "transaction_id" to "smoke-$id", "amount" to amount, "name" to name, "date" to date,
        "iso_currency_code" to "USD", "payment_channel" to "other", "pending" to false,
        "location" to emptyMap<String, Any>(), "payment_meta" to emptyMap<String, Any>(),
        "personal_finance_category" to mapOf("primary" to primary, "detailed" to detailed),
    )

    val purchase = transaction("purchase", 18.75, "Smoke Groceries", "FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES")
    val income = transaction("income", -48, "Smoke Pay", "INCOME", "INCOME_SALARY")
    val transferOut = transaction("transfer-out", 30, "Smoke Savings Transfer", "TRANSFER_OUT", "TRANSFER_OUT_ACCOUNT_TRANSFER")
    val transferIn = transaction("transfer-in", -30, "Smoke Savings Transfer", "TRANSFER_IN", "TRANSFER_IN_ACCOUNT_TRANSFER", "bbb")
    val history = transaction("history", 8, "Smoke History", "FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES")
    val new = transaction("new", 4.5, "Smoke Coffee", "FOOD_AND_DRINK", "FOOD_AND_DRINK_COFFEE")

    fun batchPage(offset: Int, transactions: List<Map<String, Any>>) = PlaidExchange(
        "/transactions/get", mapOf("offset" to offset, "count" to 3, "account_ids" to plaidAccountIds), mapOf(
            "accounts" to plaidAccounts, "request_id" to "smoke-get", "transactions" to transactions,
            "total_transactions" to 4, "item" to mapOf(
                "item_id" to "smoke-item", "available_products" to emptyList<String>(),
                "billed_products" to listOf("transactions"), "update_type" to "background",
            ),
        ),
    )

    fun syncPage(
        cursor: String?, nextCursor: String, added: List<Map<String, Any>> = emptyList(),
        modified: List<Map<String, Any>> = emptyList(), removed: List<Map<String, Any>> = emptyList(), hasMore: Boolean = false,
    ) = PlaidExchange("/transactions/sync", mapOf("cursor" to cursor), mapOf(
        "accounts" to plaidAccounts, "request_id" to "smoke-sync", "added" to added, "modified" to modified,
        "removed" to removed, "has_more" to hasMore, "next_cursor" to nextCursor,
        "transactions_update_status" to "HISTORICAL_UPDATE_COMPLETE",
    ))

    fun phase(name: String, mode: String, vararg exchanges: PlaidExchange): Map<String, SmokeTransaction> {
        println(name)
        PlaidStub(exchanges.toList(), plaidToken).use { stub ->
            run.connector(firefly, mapOf(
                "syncMode" to mode, "timeZone" to "UTC", "batch" to mapOf("setInitialBalance" to false),
                "plaid" to mapOf("url" to stub.url, "clientId" to plaidClientId, "secret" to plaidSecret,
                    "batchSize" to 3, "maxRetries" to 1),
                "accounts" to plaidAccountIds.zip(listOf(checking, savings)).map { (plaidId, fireflyId) ->
                    mapOf("plaidAccountId" to plaidId, "fireflyAccountId" to fireflyId, "plaidItemAccessToken" to plaidToken)
                },
                "polled" to mapOf("syncFrequencyMinutes" to POLL_INTERVAL_MINUTES, "cursorFileDirectoryPath" to run.work.toString()),
            ))
            stub.verifyRequests()
        }
        return firefly.transactions()
    }

    val cursor = run.work.resolve("plaid_sync_cursors.txt")
    val firstPage = batchPage(0, listOf(purchase, income, transferOut))
    val secondPage = batchPage(3, listOf(transferIn))
    val batch = phase("Batch backfill", "batch", firstPage, secondPage)
    expect("Batch external IDs", batch.keys, setOf("plaid-smoke-income", "plaid-smoke-purchase", "plaid-smoke-transfer-in"))
    checkTransaction(batch, "purchase", "withdrawal", "18.75", "source_id" to checking.toString())
    checkTransaction(batch, "income", "deposit", "48", "destination_id" to checking.toString())
    checkTransaction(batch, "transfer-in", "transfer", "30", "source_id" to checking.toString(), "destination_id" to savings.toString())
    val tags = batch.getValue("plaid-smoke-purchase").split.required("tags").map(JsonNode::asText)
    expect("Purchase has the Plaid category tag", "plaid-primary-cat-food-and-drink" in tags, true)

    fun transactionIds(snapshot: Map<String, SmokeTransaction>) = snapshot.mapValues { it.value.id }

    val rerun = phase("Batch rerun", "batch", firstPage, secondPage)
    expect("Batch rerun transaction IDs", transactionIds(rerun), transactionIds(batch))

    val initialized = phase("Polled cursor initialization", "polled",
        syncPage(null, "baseline", added = listOf(history)), syncPage("baseline", "baseline"))
    expect("Transaction IDs after history catch-up", transactionIds(initialized), transactionIds(batch))
    expect("Initialized cursor", cursor.readText(), "$plaidToken|baseline")

    val delta = phase("Polled delta after restart", "polled",
        syncPage("baseline", "delta-page-2", added = listOf(new), hasMore = true),
        syncPage("delta-page-2", "delta-complete", modified = listOf(purchase + ("amount" to 21.25)),
            removed = listOf(mapOf("account_id" to "aaa", "transaction_id" to "smoke-income"))))
    expect("Delta external IDs", delta.keys, setOf("plaid-smoke-new", "plaid-smoke-purchase", "plaid-smoke-transfer-in"))
    for (id in listOf("plaid-smoke-purchase", "plaid-smoke-transfer-in")) {
        expect("Firefly ID for $id after delta", delta.getValue(id).id, batch.getValue(id).id)
    }
    checkTransaction(delta, "purchase", "withdrawal", "21.25", "source_id" to checking.toString())
    checkTransaction(delta, "new", "withdrawal", "4.50", "source_id" to checking.toString())
    expect("Cursor after delta", cursor.readText(), "$plaidToken|delta-complete")

    val noop = phase("Polled restart with no changes", "polled", syncPage("delta-complete", "delta-complete"))
    expect("Firefly transactions after empty poll", noop, delta)
    expect("Cursor after empty poll", cursor.readText(), "$plaidToken|delta-complete")
}

private fun checkTransaction(
    snapshot: Map<String, SmokeTransaction>, id: String, type: String, amount: String, vararg fields: Pair<String, String>,
) {
    val externalId = "plaid-smoke-$id"
    val split = snapshot.getValue(externalId).split
    val actual = split.required("amount").asText().toBigDecimal().stripTrailingZeros()
    expect("$externalId amount", actual, amount.toBigDecimal().stripTrailingZeros())
    for ((field, value) in listOf("type" to type, "currency_code" to "USD") + fields) {
        expect("$externalId $field", split.required(field).asText(), value)
    }
}

private class PlaidStub(exchanges: List<PlaidExchange>, private val plaidToken: String) : AutoCloseable {
    private val remaining = ArrayDeque(exchanges)
    private var mismatch: String? = null
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/", ::handle)
        start()
    }
    val url = "http://127.0.0.1:${server.address.port}"

    @Synchronized
    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val expected = remaining.peekFirst()
            val path = exchange.requestURI.path
            val request = runCatching { smokeJson.readTree(exchange.requestBody) }.getOrNull()
            val options = if (path == "/transactions/get") request?.path("options") else request
            val actual = expected?.options?.keys?.associateWith { options?.get(it) ?: NullNode.instance }
            val expectedOptions = expected?.options?.mapValues { smokeJson.valueToTree<JsonNode>(it.value) }
            if (expected != null && path == expected.path && actual == expectedOptions &&
                exchange.requestMethod == "POST" && request?.path("access_token")?.asText() == plaidToken) {
                remaining.removeFirst()
                val body = smokeJson.writeValueAsBytes(expected.response)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.write(body)
            } else {
                mismatch = "Expected Plaid ${expected?.path} $expectedOptions, received ${exchange.requestMethod} $path $actual."
                exchange.sendResponseHeaders(400, -1)
            }
        }
    }

    @Synchronized
    fun verifyRequests() {
        mismatch?.let { throw SmokeFailure(it) }
        expect("Unconsumed Plaid requests", remaining.map { it.path to it.options }, emptyList<Any>())
    }

    override fun close() = server.stop(0)
}
