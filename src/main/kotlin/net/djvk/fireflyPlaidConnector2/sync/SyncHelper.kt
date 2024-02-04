package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AboutApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AccountsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.FireflyApiError
import net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs
import net.djvk.fireflyPlaidConnector2.transactions.FireflyAccountId
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.versionManagement.VersionComparison
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

typealias PlaidAccessToken = String
typealias PlaidAccountId = String

const val MINIMUM_FIREFLY_VERSION = "6.1.2"

@Component
class SyncHelper(
    private val plaidAccountsConfig: AccountConfigs,

    @Value("\${fireflyPlaidConnector2.firefly.personalAccessToken}")
    private val fireflyAccessToken: String,
    private val fireflyAboutApi: AboutApi,
    private val fireflyTxApi: TransactionsApi,
    private val fireflyAccountsApi: AccountsApi,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun setApiCreds() {
        // Spring components are singletons by default, so this should set these credentials for any other
        //  component that also uses these components
        fireflyTxApi.setAccessToken(fireflyAccessToken)
        fireflyAccountsApi.setAccessToken(fireflyAccessToken)
        fireflyAboutApi.setAccessToken(fireflyAccessToken)
        validateFireflyApiVersion()
    }

    protected suspend fun validateFireflyApiVersion() {
        val fireflyVersion = fireflyAboutApi.getAbout().body().data.version
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
            } catch (e: ConnectTimeoutException) {
                logger.error("Timeout inserting firefly tx; skipping for now: $fireflyTx", e)
            }
        }
    }

    /**
     * The only difference between this and [optimisticInsertBatchIntoFirefly] is that this doesn't expect or tolerate
     *  duplicate errors.
     */
    suspend fun pessimisticInsertBatchIntoFirefly(fireflyTxs: List<FireflyTransactionDto>) {
        if (fireflyTxs.isNotEmpty()) {
            logger.debug("Pessimistic insert of ${fireflyTxs.size} txs into Firefly")
        }
        for (fireflyTx in fireflyTxs) {
            try {
                insertIntoFirefly(fireflyTx)
            } catch (cre: ClientRequestException) {
                val error = cre.response.body<FireflyApiError>()
                if (cre.response.status == HttpStatusCode.UnprocessableEntity) {
                    logger.error("Firefly transaction insert $error for tx: $fireflyTx")
                    throw cre
                }
            }
        }
    }

    suspend fun insertIntoFirefly(fireflyTx: FireflyTransactionDto) {
        if (fireflyTx.tx.amount.toDouble() == 0.0) {
            logger.info("Skipped transaction ${fireflyTx.tx.externalId} with amount 0.0")
            return
        }
        fireflyTxApi.storeTransaction(fireflyTx.toTransactionStore())
    }

    suspend fun updateBatchInFirefly(fireflyTxs: List<FireflyTransactionDto>) {
        for (fireflyTx in fireflyTxs) {
            fireflyTxApi.updateTransaction(
                fireflyTx.id
                    ?: throw IllegalArgumentException("Can't update Firefly transaction without id: $fireflyTx"),
                fireflyTx.toTransactionUpdate(),
            )
        }
    }

    suspend fun deleteBatchInFirefly(fireflyTxIds: List<FireflyTransactionId>) {
        if (fireflyTxIds.isNotEmpty()) {
            logger.debug("Delete batch of ${fireflyTxIds.size} txs in Firefly")
        }
        for (fireflyTxId in fireflyTxIds) {
            fireflyTxApi.deleteTransaction(fireflyTxId)
        }
    }
}