package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.plugins.*
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
     * Returns null if the request fails and allowItemToFail is true.
     */
    suspend fun executeTransactionSyncRequest(
        accessToken: PlaidAccessToken,
        cursor: PlaidSyncCursor?,
        batchSize: Int = plaidBatchSize
    ): TransactionsSyncResponse? {
        val request = getTransactionSyncRequest(accessToken, cursor, batchSize)
        try {
            return plaidApiWrapper.executeRequest(
                { plaidApi -> plaidApi.transactionsSync(request) },
                "transaction sync request"
            ).body()
        } catch (cre: ClientRequestException) {
            logger.error("Error requesting Plaid transactions. Request: $request; ")
            if (allowItemToFail) {
                logger.warn("Querying transactions for access token $accessToken failed, allowing failure and continuing on to the next access token")
                return null
            } else throw cre
        }
    }

    /**
     * Processes Plaid transactions for a set of access tokens and account IDs.
     * Returns lists of created, updated, and deleted transactions.
     */
    suspend fun processPlaidTransactions(
        accountAccessTokenSequence: Sequence<Pair<PlaidAccessToken, List<PlaidAccountId>>>,
        cursorMap: MutableMap<PlaidAccessToken, PlaidSyncCursor>
    ): PlaidTransactionResult {
        val plaidCreatedTxs = mutableListOf<PlaidTransaction>()
        val plaidUpdatedTxs = mutableListOf<PlaidTransaction>()
        val plaidDeletedTxs = mutableListOf<PlaidTransactionId>()
        var backfillCount: Int? = null

        accessTokenLoop@ for ((accessToken, accountIds) in accountAccessTokenSequence) {
            logger.debug(
                "Querying Plaid transaction sync endpoint for access token $accessToken " +
                        " and account ids ${accountIds.joinToString("; ")}"
            )
            val accountIdSet = accountIds.toSet()
            var cursor = cursorMap[accessToken]
            val cutoff = initialBackfillAfter.takeIf { cursor.isNullOrBlank() }
            val backfill = cutoff?.let { mutableListOf<PlaidTransaction>() }
            if (backfill != null && backfillCount == null) backfillCount = 0

            // Plaid transaction batch loop
            do {
                // Iterate through batches of Plaid transactions
                // In sync mode we fetch and retain all Plaid transactions that have changed since the last poll.
                val response = executeTransactionSyncRequest(
                    accessToken,
                    cursor,
                    plaidBatchSize
                ) ?: continue@accessTokenLoop

                cursor = response.nextCursor
                logger.debug(
                    "Received batch of sync updates for access token $accessToken: " +
                            "${response.added.size} created; ${response.modified.size} updated; " +
                            "${response.removed.size} deleted; next cursor ${response.nextCursor}"
                )

                if (cutoff != null && backfill != null) {
                    // Plaid returns an initial snapshot as adds. Retain recent rows for mapped accounts.
                    response.added.filterTo(backfill) {
                        it.accountId in accountIdSet && it.date.isAfter(cutoff) &&
                            (initialBackfillThrough == null || it.date <= initialBackfillThrough)
                    }
                } else {
                    cursorMap[accessToken] = response.nextCursor
                    // The transaction sync endpoint doesn't take accountId as a parameter, so do that filtering here.
                    val added = response.added.filter { it.accountId in accountIdSet }
                    val modified = response.modified.filter { it.accountId in accountIdSet }
                    val removed = response.removed.filter { it.accountId in accountIdSet }
                    plaidCreatedTxs.addAll(added)
                    plaidUpdatedTxs.addAll(modified)
                    plaidDeletedTxs.addAll(removed.mapNotNull { it.transactionId })
                    added.forEach {
                        logger.debug(
                            "Adding transaction: (id={}; account={}; desc={}; merchant={}; date={}; amount={}; pending={}; pendingId={})",
                            it.transactionId, it.accountId, it.originalDescription, it.merchantName, it.date, it.amount,
                            it.pending, it.pendingTransactionId
                        )
                    }
                    modified.forEach {
                        logger.debug(
                            "Modifying transaction: (id={}; account={}; desc={}; merchant={}; date={}; amount={}; pending={}; pendingId={})",
                            it.transactionId, it.accountId, it.originalDescription, it.merchantName, it.date, it.amount,
                            it.pending, it.pendingTransactionId
                        )
                    }
                    removed.forEach {
                        logger.debug("Removing transaction: (id={}; account={})", it.transactionId, it.accountId)
                    }
                }

                // Keep going until we get all the transactions
            } while (response.hasMore)

            if (backfill != null && !cursor.isNullOrBlank()) {
                // Commit the complete item's history and cursor together after all pages arrive.
                cursorMap[accessToken] = cursor
                plaidCreatedTxs.addAll(backfill)
                backfillCount = (backfillCount ?: 0) + backfill.count { it.amount != 0.0 }
            }
        }

        backfillCount?.let { logger.info("$INITIAL_BACKFILL_COUNT_MESSAGE: {}", it) }
        return PlaidTransactionResult(
            plaidCreatedTxs,
            plaidUpdatedTxs,
            plaidDeletedTxs
        )
    }

    /**
     * Initializes cursors for access tokens that don't have one yet.
     */
    suspend fun initializeCursors(
        accountAccessTokenSequence: Sequence<Pair<PlaidAccessToken, List<PlaidAccountId>>>,
        cursorMap: MutableMap<PlaidAccessToken, PlaidSyncCursor>
    ) {
        // Let the first poll process the selected history before saving cursors for fresh items.
        if (initialBackfillAfter != null) return
        logger.debug("Beginning Plaid sync endpoint cursor initialization")
        cursorCatchupLoop@ for ((accessToken, _) in accountAccessTokenSequence) {
            // If we already have a cursor for this access token, then move on
            if (cursorMap.contains(accessToken)) {
                logger.debug("Cursor map contains $accessToken, skipping initialization for it")
                continue
            }

            // For access tokens that we don't have cursors for, iterate through historical data and ignore it
            // to get current cursors
            do {
                val response =
                    executeTransactionSyncRequest(accessToken, cursorMap[accessToken], plaidBatchSize)
                        ?: continue@cursorCatchupLoop
                logger.debug(
                    "Received initial batch of sync updates for access token $accessToken. " +
                            "Updating cursor map to next cursor: ${response.nextCursor}"
                )
                if (response.nextCursor.isNotBlank()) {
                    cursorMap[accessToken] = response.nextCursor
                }
            } while (response.hasMore)
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
