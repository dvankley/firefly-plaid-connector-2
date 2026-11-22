package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AboutApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AccountsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.SearchApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.FireflyApiError
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionStore
import net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs
import net.djvk.fireflyPlaidConnector2.transactions.FireflyAccountId
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.versionManagement.VersionComparison
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

typealias PlaidAccessToken = String
typealias PlaidAccountId = String

internal val fireflySearchExternalIdPattern = Regex("[A-Za-z0-9_-]+")

const val MINIMUM_FIREFLY_VERSION = "6.1.2"

@Component
class SyncHelper(
    private val plaidAccountsConfig: AccountConfigs,

    @Value("\${fireflyPlaidConnector2.firefly.personalAccessToken}")
    private val fireflyAccessToken: String,
    private val fireflyAboutApi: AboutApi,
    private val fireflyTxApi: TransactionsApi,
    private val fireflyAccountsApi: AccountsApi,
    private val fireflyApiWrapper: FireflyApiWrapper,
    private val fireflySearchApi: SearchApi,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun setApiCreds() {
        // Spring components are singletons by default, so this should set these credentials for any other
        //  component that also uses these components
        fireflyTxApi.setAccessToken(fireflyAccessToken)
        fireflyAccountsApi.setAccessToken(fireflyAccessToken)
        fireflyAboutApi.setAccessToken(fireflyAccessToken)
        fireflySearchApi.setAccessToken(fireflyAccessToken)
        validateFireflyApiVersion()
    }

    protected suspend fun validateFireflyApiVersion() {
        val fireflyVersion = fireflyApiWrapper.executeRequest("get Firefly system info") {
            fireflyAboutApi.getAbout()
        }.body().data.version
        if (!VersionComparison.isVersionSufficient(MINIMUM_FIREFLY_VERSION, fireflyVersion)) {
            throw RuntimeException("This version of the connector requires at least version $MINIMUM_FIREFLY_VERSION " +
                "of Firefly; version $fireflyVersion found")
        }
    }

    fun getAllPlaidAccessTokenAccountIdSets():
            Pair<Map<PlaidAccountId, FireflyAccountId>, Sequence<Pair<PlaidAccessToken, List<PlaidAccountId>>>> {
        val accountMap = plaidAccountsConfig.accounts.associate { Pair(it.plaidAccountId, it.fireflyAccountId) }
        logger.trace("Read config mapping data for ${accountMap.size} Firefly accounts")
        val accountsByAccessToken = plaidAccountsConfig.accounts.groupBy { it.plaidItemAccessToken }
        logger.trace("Read config mapping data for ${accountsByAccessToken.size} Plaid access tokens")

        return Pair(accountMap, sequence {
            for ((accessToken, accountConfigs) in accountsByAccessToken) {
                val accountIds = accountConfigs.map { it.plaidAccountId }
                yield(Pair(accessToken, accountIds))
            }
        })
    }

    suspend fun optimisticInsertBatchIntoFirefly(fireflyTxs: List<FireflyTransactionDto>) {
        if (fireflyTxs.isNotEmpty()) {
            logger.debug("Optimistic insert of ${fireflyTxs.size} txs into Firefly")
        }
        var index = 0
        for (fireflyTx in fireflyTxs) {
            try {
                insertIntoFirefly(fireflyTx)
                index++
                if (index % 100 == 0) {
                    logger.debug("Insert of tx index $index successful")
                }
            } catch (cre: ClientRequestException) {
                if (cre.response.status == HttpStatusCode.UnprocessableEntity) {
                    val error = cre.response.body<FireflyApiError>()
                    if (error.message.lowercase().contains("duplicate of transaction")) {
                        logger.info("Skipped transaction ${fireflyTx.tx.externalId} that Firefly identified as a duplicate")
                    } else {
                        logger.error("Firefly transaction insert $error for tx: $fireflyTx")
                        throw cre
                    }
                } else {
                    throw cre
                }
            }
        }
    }

    suspend fun insertTransferReplacement(replacement: TransactionStore) {
        val externalId = replacement.transactions.single().externalId
        // An ambiguous POST must go through journal reconciliation before another attempt.
        fireflyApiWrapper.executeRequest("insert transfer replacement $externalId", isRetryable = { false }) {
            val response = fireflyTxApi.storeTransaction(replacement)
            check(response.success) { "Firefly rejected transfer replacement $externalId (HTTP ${response.status})" }
        }
    }

    suspend fun findTransactionsByExternalId(externalId: String): List<TransactionRead> {
        require(externalId.matches(fireflySearchExternalIdPattern)) { "Invalid external ID for Firefly search" }
        val matches = mutableListOf<TransactionRead>()
        for (page in 1..20) {
            val result = fireflyApiWrapper.executeRequest("search Firefly transactions for $externalId, page $page") {
                val response = fireflySearchApi.searchTransactions("external_id_is:$externalId", page)
                check(response.success) { "Firefly search failed for $externalId (HTTP ${response.status})" }
                response.body()
            }
            matches += result.data.filter { group -> group.attributes.transactions.any { it.externalId == externalId } }
            val pagination = result.meta.pagination
            if (pagination == null || pagination.currentPage >= pagination.totalPages) return matches
        }
        error("Exceeded Firefly search page limit for $externalId")
    }

    suspend fun insertIntoFirefly(fireflyTx: FireflyTransactionDto) {
        if (fireflyTx.tx.amount.toDouble() == 0.0) {
            logger.info("Skipped transaction ${fireflyTx.tx.externalId} with amount 0.0")
            return
        }
        fireflyApiWrapper.executeRequest("insert transaction ${fireflyTx.tx.externalId}") {
            fireflyTxApi.storeTransaction(fireflyTx.toTransactionStore())
        }
    }

    suspend fun updateBatchInFirefly(fireflyTxs: List<FireflyTransactionDto>) {
        for (fireflyTx in fireflyTxs) {
            val id = fireflyTx.id
                ?: throw IllegalArgumentException("Can't update Firefly transaction without id: $fireflyTx")
            fireflyApiWrapper.executeRequest("update transaction $id") {
                fireflyTxApi.updateTransaction(id, fireflyTx.toTransactionUpdate())
            }
        }
    }

    suspend fun deleteBatchInFirefly(fireflyTxIds: List<FireflyTransactionId>) {
        if (fireflyTxIds.isNotEmpty()) {
            logger.debug("Delete batch of ${fireflyTxIds.size} txs in Firefly")
        }
        for (fireflyTxId in fireflyTxIds) {
            try {
                fireflyApiWrapper.executeRequest("delete transaction $fireflyTxId") {
                    fireflyTxApi.deleteTransaction(fireflyTxId)
                }
            } catch (cre: ClientRequestException) {
                if (cre.response.status == HttpStatusCode.NotFound) {
                    /**
                     * A delete can succeed on the server but still throw here if the response never made
                     *  it back (e.g. a connection drop right after Firefly processed it), triggering a
                     *  retry against an already-deleted transaction, which Firefly reports as not found.
                     * A delete's desired end state is "the transaction doesn't exist", so a 404 here means
                     *  that goal is already met; treat it as success rather than a failure.
                     */
                    logger.info(
                        "Transaction $fireflyTxId not found while deleting; treating as already deleted, " +
                                "possibly due to a retried request whose earlier success wasn't acknowledged"
                    )
                } else {
                    throw cre
                }
            }
        }
    }
}
