package net.djvk.fireflyPlaidConnector2.sync

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidTransactionId
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequestOptions
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Component
import java.time.LocalDate
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

internal const val INITIAL_BACKFILL_COUNT_MESSAGE = "Initial polled backfill eligible transactions"
private const val PAGINATION_MUTATION = "TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION"
private const val MAX_PAGINATION_RESTARTS = 2

/**
 * Service for handling Plaid transaction synchronization.
 */
@Component
class PlaidSyncService(
    private val plaidApiWrapper: PlaidApiWrapper,

    @Value("\${fireflyPlaidConnector2.plaid.batchSize}")
    private val plaidBatchSize: Int,

    @Value("\${fireflyPlaidConnector2.polled.allowItemToFail:false}")
    private val allowItemToFail: Boolean,

    @Value("\${fireflyPlaidConnector2.polled.initialBackfillAfter:}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private val initialBackfillAfter: LocalDate? = null,

    @Value("\${fireflyPlaidConnector2.polled.initialBackfillThrough:}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private val initialBackfillThrough: LocalDate? = null,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errorJson = ObjectMapper()

    /**
     * Creates a transaction sync request for Plaid API.
     */
    fun getTransactionSyncRequest(
        accessToken: PlaidAccessToken,
        cursor: PlaidSyncCursor?,
        batchSize: Int = plaidBatchSize
    ): TransactionsSyncRequest {
        return TransactionsSyncRequest(
            accessToken,
            null,
            null,
            cursor,
            batchSize,
            TransactionsSyncRequestOptions(
                includeOriginalDescription = true,
                includePersonalFinanceCategory = true,
            )
        )
    }

    /**
     * Executes a transaction sync request to Plaid API.
     */
    suspend fun executeTransactionSyncRequest(
        accessToken: PlaidAccessToken,
        cursor: PlaidSyncCursor?,
        batchSize: Int = plaidBatchSize
    ): TransactionsSyncResponse {
        val request = getTransactionSyncRequest(accessToken, cursor, batchSize)
        return plaidApiWrapper.executeRequest(
            { plaidApi -> plaidApi.transactionsSync(request) },
            "transaction sync request"
        ).body()
    }

    /**
     * Processes Plaid transactions for a set of access tokens and account IDs.
     * Returns lists of created, updated, and deleted transactions.
     */
    suspend fun processPlaidTransactions(
        accountAccessTokenSequence: Sequence<Pair<PlaidAccessToken, List<PlaidAccountId>>>,
        cursorMap: MutableMap<PlaidAccessToken, PlaidSyncCursor>,
        requireComplete: Boolean = false,
    ): PlaidTransactionResult {
        val plaidCreatedTxs = mutableListOf<PlaidTransaction>()
        val plaidUpdatedTxs = mutableListOf<PlaidTransaction>()
        val plaidDeletedTxs = mutableListOf<PlaidTransactionId>()
        var backfillCount: Int? = null

        for ((accessToken, accountIds) in accountAccessTokenSequence) {
            val startingCursor = cursorMap[accessToken]?.takeUnless { it.isBlank() }
            val cutoff = initialBackfillAfter.takeIf { startingCursor == null }
            if (cutoff != null && backfillCount == null) backfillCount = 0
            // A failed or not-yet-ready initialization can leave the item cursorless on later polls.
            val skipHistory = startingCursor == null && cutoff == null
            val update = fetchItemUpdate(
                accessToken,
                startingCursor,
                accountIds = if (skipHistory) null else accountIds.toSet(),
                backfillAfter = cutoff,
                requireComplete = requireComplete,
            ) ?: continue

            cursorMap[accessToken] = update.cursor
            plaidCreatedTxs.addAll(update.transactions.created)
            plaidUpdatedTxs.addAll(update.transactions.updated)
            plaidDeletedTxs.addAll(update.transactions.deleted)
            if (cutoff != null) {
                backfillCount = (backfillCount ?: 0) + update.transactions.created.count { it.amount != 0.0 }
            }
        }

        backfillCount?.let { logger.info("$INITIAL_BACKFILL_COUNT_MESSAGE: {}", it) }
        return PlaidTransactionResult(plaidCreatedTxs, plaidUpdatedTxs, plaidDeletedTxs)
    }

    /**
     * Initializes cursors for access tokens that don't have one yet.
     */
    suspend fun initializeCursors(
        accountAccessTokenSequence: Sequence<Pair<PlaidAccessToken, List<PlaidAccountId>>>,
        cursorMap: MutableMap<PlaidAccessToken, PlaidSyncCursor>
    ) {
        // Let the first poll process the selected history for fresh items.
        if (initialBackfillAfter != null) return
        logger.debug("Beginning Plaid sync endpoint cursor initialization")
        for ((accessToken, _) in accountAccessTokenSequence) {
            if (!cursorMap[accessToken].isNullOrBlank()) continue
            val update = fetchItemUpdate(accessToken, startingCursor = null) ?: continue
            cursorMap[accessToken] = update.cursor
        }
    }

    private data class ItemUpdate(val cursor: PlaidSyncCursor, val transactions: PlaidTransactionResult)

    private suspend fun fetchItemUpdate(
        accessToken: PlaidAccessToken,
        startingCursor: PlaidSyncCursor?,
        accountIds: Set<PlaidAccountId>? = null,
        backfillAfter: LocalDate? = null,
        requireComplete: Boolean = false,
    ): ItemUpdate? {
        var restarts = 0
        while (true) {
            var cursor = startingCursor
            val added = mutableListOf<PlaidTransaction>()
            val modified = mutableListOf<PlaidTransaction>()
            val removed = mutableListOf<PlaidTransactionId>()

            try {
                do {
                    val page = executeTransactionSyncRequest(accessToken, cursor)
                    cursor = page.nextCursor
                    logger.debug(
                        "Received Plaid sync page with {} adds, {} modifications and {} removals",
                        page.added.size, page.modified.size, page.removed.size,
                    )
                    if (accountIds == null) continue

                    if (backfillAfter != null) {
                        // The initial snapshot contains adds. Apply the configured date range.
                        page.added.filterTo(added) {
                            it.accountId in accountIds && it.date.isAfter(backfillAfter) &&
                                (initialBackfillThrough == null || it.date <= initialBackfillThrough)
                        }
                    } else {
                        page.added.filterTo(added) { it.accountId in accountIds }
                        page.modified.filterTo(modified) { it.accountId in accountIds }
                        page.removed.mapNotNullTo(removed) { (transactionId, accountId) ->
                            if (accountId in accountIds) transactionId else null
                        }
                    }
                } while (page.hasMore)
            } catch (failure: ClientRequestException) {
                if (restarts < MAX_PAGINATION_RESTARTS && isPaginationMutation(failure)) {
                    restarts++
                    logger.warn(
                        "Plaid transactions changed during pagination. Restarting item update ({}/{})",
                        restarts, MAX_PAGINATION_RESTARTS,
                    )
                    continue
                }
                if (!allowItemToFail) throw failure
                if (requireComplete) {
                    throw IllegalStateException("Plaid replay is incomplete while transfer replacements are pending", failure)
                }
                logger.warn("Plaid item query failed with allowItemToFail enabled (HTTP {})", failure.response.status)
                return null
            }

            val finalCursor = cursor?.takeUnless { it.isBlank() } ?: return null
            return ItemUpdate(finalCursor, PlaidTransactionResult(added, modified, removed))
        }
    }

    private suspend fun isPaginationMutation(failure: ClientRequestException): Boolean {
        return try {
            errorJson.readTree(failure.response.bodyAsText())?.path("error_code")?.asText() == PAGINATION_MUTATION
        } catch (_: JsonProcessingException) {
            false
        }
    }
}

/**
 * Data class to hold the result of processing Plaid transactions.
 */
data class PlaidTransactionResult(
    val created: List<PlaidTransaction>,
    val updated: List<PlaidTransaction>,
    val deleted: List<PlaidTransactionId>
)
