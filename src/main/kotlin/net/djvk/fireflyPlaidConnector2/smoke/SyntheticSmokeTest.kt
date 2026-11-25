package net.djvk.fireflyPlaidConnector2.smoke

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlin.io.path.exists
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

    val oldDate = LocalDate.now(ZoneOffset.UTC).minusDays(30).toString()
    val purchase = transaction("purchase", 18.75, "Smoke Groceries", "FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES") + ("date" to oldDate)
    val income = transaction("income", -48, "Smoke Pay", "INCOME", "INCOME_SALARY") + ("date" to oldDate)
    val transferOut = transaction("transfer-out", 30, "Smoke Savings Transfer", "TRANSFER_OUT", "TRANSFER_OUT_ACCOUNT_TRANSFER")
    val transferIn = transaction("transfer-in", -30, "Smoke Savings Transfer", "TRANSFER_IN", "TRANSFER_IN_ACCOUNT_TRANSFER", "bbb")
    val history = transaction("history", 8, "Smoke History", "FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES")
    val new = transaction("new", 4.5, "Smoke Coffee", "FOOD_AND_DRINK", "FOOD_AND_DRINK_COFFEE")
    val laterOut = transaction("later-out", 65, "Late Savings Transfer", "TRANSFER_OUT", "TRANSFER_OUT_ACCOUNT_TRANSFER")
    val laterIn = transaction("later-in", -65, "Late Savings Transfer", "TRANSFER_IN", "TRANSFER_IN_ACCOUNT_TRANSFER", "bbb")

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

    fun phase(
        name: String,
        mode: String,
        vararg exchanges: PlaidExchange,
        fireflyUrl: String = firefly.baseUrl,
        onPollDeferred: (() -> Unit)? = null,
        allowSplits: Boolean = false,
    ): Map<String, SmokeTransaction> {
        println(name)
        PlaidStub(exchanges.toList(), plaidToken).use { stub ->
            run.connector(firefly, mapOf(
                "syncMode" to mode, "timeZone" to "UTC", "batch" to mapOf("setInitialBalance" to false),
                "plaid" to mapOf("url" to stub.url, "clientId" to plaidClientId, "secret" to plaidSecret,
                    "batchSize" to 3, "maxRetries" to 1),
                "accounts" to plaidAccountIds.zip(listOf(checking, savings)).map { (plaidId, fireflyId) ->
                    mapOf("plaidAccountId" to plaidId, "fireflyAccountId" to fireflyId, "plaidItemAccessToken" to plaidToken)
                },
                "polled" to mapOf(
                    "syncFrequencyMinutes" to if (onPollDeferred == null) POLL_INTERVAL_MINUTES else 1,
                    "cursorFileDirectoryPath" to run.work.toString(),
                ),
            ), fireflyUrl = fireflyUrl, onPollDeferred = onPollDeferred)
            stub.verifyRequests()
        }
        return firefly.transactions(allowSplits)
    }

    val cursor = run.work.resolve("plaid_sync_cursors.txt")
    val firstPage = batchPage(0, listOf(purchase, income, transferOut))
    val secondPage = batchPage(3, listOf(transferIn))
    val batch = phase("Batch backfill", "batch", firstPage, secondPage)
    expect("Batch external IDs", batch.keys, setOf("plaid-smoke-income", "plaid-smoke-purchase", "plaid-smoke-transfer-in"))
    checkTransaction(batch, "purchase", "withdrawal", "18.75", "source_id" to checking.toString())
    checkTransaction(batch, "income", "deposit", "48", "destination_id" to checking.toString())
    checkTransaction(batch, "transfer-in", "transfer", "30", "source_id" to checking.toString(),
        "destination_id" to savings.toString(), "internal_reference" to "plaid-transfer-leg-smoke-transfer-out")
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
        syncPage("baseline", "delta-page-2", added = listOf(new, laterOut), hasMore = true),
        syncPage("delta-page-2", "delta-complete", modified = listOf(purchase + ("amount" to 21.25)),
            removed = listOf(mapOf("account_id" to "aaa", "transaction_id" to "smoke-income"))))
    expect("Delta external IDs", delta.keys, setOf(
        "plaid-smoke-new", "plaid-smoke-purchase", "plaid-smoke-transfer-in", "plaid-smoke-later-out",
    ))
    for (id in listOf("plaid-smoke-purchase", "plaid-smoke-transfer-in")) {
        expect("Firefly ID for $id after delta", delta.getValue(id).id, batch.getValue(id).id)
    }
    checkTransaction(delta, "purchase", "withdrawal", "21.25", "source_id" to checking.toString())
    checkTransaction(delta, "new", "withdrawal", "4.50", "source_id" to checking.toString())
    checkTransaction(delta, "later-out", "withdrawal", "65", "source_id" to checking.toString())
    expect("Cursor after delta", cursor.readText(), "$plaidToken|delta-complete")

    val journal = run.work.resolve("pending_transfer_replacements.json")
    val replacementPage = syncPage("delta-complete", "replacement-complete", added = listOf(laterIn))
    val recovered = FireflyResponseDropper(firefly.baseUrl).use { proxy ->
        lateinit var afterFailure: Map<String, SmokeTransaction>
        val afterRecovery = phase("Polled transfer replacement recovery in the same process", "polled",
            replacementPage, replacementPage, fireflyUrl = proxy.url, onPollDeferred = {
                expect("Successful replacement response dropped", proxy.droppedResponse, true)
                expect("Replacement POSTs after response loss", proxy.transactionPosts, 1)
                expect("Cursor after the failed replacement poll", cursor.readText(), "$plaidToken|delta-complete")
                expect("Transfer replacement journal after response loss", journal.exists(), true)
                afterFailure = firefly.transactions()
            })
        expect("Replacement external IDs", afterFailure.keys, delta.keys - "plaid-smoke-later-out" + "plaid-smoke-later-in")
        checkTransaction(afterFailure, "later-in", "transfer", "65",
            "source_id" to checking.toString(), "destination_id" to savings.toString(),
            "internal_reference" to "plaid-transfer-leg-smoke-later-out")
        expect("Replacement POSTs after recovery", proxy.transactionPosts, 1)
        expect("Firefly transactions after recovery", afterRecovery, afterFailure)
        expect("Cursor after replacement recovery", cursor.readText(), "$plaidToken|replacement-complete")
        expect("Transfer replacement journal after recovery", journal.exists(), false)
        afterRecovery
    }

    val noop = phase("Polled restart with no changes", "polled", syncPage("replacement-complete", "replacement-complete"))
    expect("Firefly transactions after empty poll", noop, recovered)
    expect("Cursor after empty poll", cursor.readText(), "$plaidToken|replacement-complete")

    firefly.request("POST", "/transactions", mapOf(
        "group_title" to "Smoke split withdrawal",
        "transactions" to listOf("split-first", "split-second").map { leg ->
            mapOf("type" to "withdrawal", "date" to date, "amount" to "3.00", "description" to leg,
                "source_id" to checking.toString(), "destination_name" to "Smoke Split Merchant",
                "external_id" to "plaid-smoke-$leg", "currency_code" to "USD")
        },
    ))
    val beforeReview = firefly.transactions(allowSplits = true)
    expect("Split group ID", beforeReview.getValue("plaid-smoke-split-first").id,
        beforeReview.getValue("plaid-smoke-split-second").id)
    val afterReview = phase("Polled transfer and split corrections", "polled",
        syncPage("replacement-complete", "review-complete",
            modified = listOf(transferOut + ("amount" to 31), laterIn + ("amount" to -66)),
            removed = listOf("transfer-in" to "bbb", "later-out" to "aaa", "split-first" to "aaa").map { (id, account) ->
                mapOf("transaction_id" to "smoke-$id", "account_id" to account)
            }),
        allowSplits = true,
    )
    expect("Firefly transactions after deferred corrections", afterReview, beforeReview)
    expect("Cursor after deferred corrections", cursor.readText(), "$plaidToken|review-complete")
    val queue = smokeJson.readTree(run.work.resolve("plaid_unresolved_changes.json").readText()).required("entries")
    val expectedTargets = mapOf(
        "transfer-in" to "transfer-in", "transfer-out" to "transfer-in",
        "later-in" to "later-in", "later-out" to "later-in", "split-first" to "split-first",
    ).mapKeys { (id, _) -> "smoke-$id" }
    expect("Deferred Plaid IDs", queue.map { it.required("transactionId").asText() }.toSet(), expectedTargets.keys)
    for (entry in queue) {
        val id = entry.required("transactionId").asText()
        val expected = beforeReview.getValue("plaid-smoke-${expectedTargets.getValue(id)}")
        expect("Correction status for $id", entry.required("status").asText(), "needs_review")
        val target = entry.required("reviewTargets").single()
        expect("Review group ID for $id", target.required("groupId").asText(), expected.id)
        expect("Review journal ID for $id", target.required("journalId").asText(),
            expected.split.required("transaction_journal_id").asText())
    }
}

private class FireflyResponseDropper(private val baseUrl: String) : AutoCloseable {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/", ::handle)
        start()
    }
    val url = "http://127.0.0.1:${server.address.port}"
    @Volatile var droppedResponse = false
        private set
    @Volatile var transactionPosts = 0
        private set

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val isTransactionPost = exchange.requestMethod == "POST" && exchange.requestURI.path == "/api/v1/transactions"
            if (isTransactionPost) transactionPosts++
            val body = exchange.requestBody.readAllBytes()
            val request = HttpRequest.newBuilder(URI("$baseUrl${exchange.requestURI}"))
                .timeout(Duration.ofSeconds(15))
                .method(exchange.requestMethod, HttpRequest.BodyPublishers.ofByteArray(body))
            for (header in listOf("Accept", "Authorization", "Content-Type")) {
                exchange.requestHeaders.getFirst(header)?.let { request.header(header, it) }
            }
            val response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
            if (isTransactionPost && !droppedResponse && response.statusCode() in 200..299) {
                droppedResponse = true
                // Firefly committed the transfer. Close the client connection before sending HTTP headers.
                return
            }
            response.headers().firstValue("Content-Type").ifPresent { exchange.responseHeaders.set("Content-Type", it) }
            val bytes = response.body()
            exchange.sendResponseHeaders(response.statusCode(), if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
        }
    }

    override fun close() = server.stop(0)
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
