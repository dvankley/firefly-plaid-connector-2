package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.plugins.*
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidTransactionId
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncRequestOptions
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsSyncResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

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

        accessTokenLoop@ for ((accessToken, accountIds) in accountAccessTokenSequence) {
            logger.debug(
                "Querying Plaid transaction sync endpoint for access token $accessToken " +
                        " and account ids ${accountIds.joinToString("; ")}"
            )
            val accountIdSet = accountIds.toSet()

            // Plaid transaction batch loop
            do {
                // Iterate through batches of Plaid transactions
                // In sync mode we fetch and retain all Plaid transactions that have changed since the last poll.
                val response = executeTransactionSyncRequest(
                    accessToken,
                    cursorMap[accessToken],
                    plaidBatchSize
                ) ?: continue@accessTokenLoop

                cursorMap[accessToken] = response.nextCursor
                logger.debug(
                    "Received batch of sync updates for access token $accessToken: " +
                            "${response.added.size} created; ${response.modified.size} updated; " +
                            "${response.removed.size} deleted; next cursor ${response.nextCursor}"
                )

                // The transaction sync endpoint doesn't take accountId as a parameter, so do that filtering here
                plaidCreatedTxs.addAll(response.added.filter { accountIdSet.contains(it.accountId) })
                plaidUpdatedTxs.addAll(response.modified.filter { accountIdSet.contains(it.accountId) })
                plaidDeletedTxs.addAll(response.removed.mapNotNull { it.transactionId })

                // Keep going until we get all the transactions
            } while (response.hasMore)
        }

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