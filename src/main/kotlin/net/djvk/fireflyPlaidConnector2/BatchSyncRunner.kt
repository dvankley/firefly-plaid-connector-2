package net.djvk.fireflyPlaidConnector2

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.FireflyApiError
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionStore
import net.djvk.fireflyPlaidConnector2.api.plaid.apis.PlaidApi
import net.djvk.fireflyPlaidConnector2.api.plaid.infrastructure.clientIdHeader
import net.djvk.fireflyPlaidConnector2.api.plaid.infrastructure.secretHeader
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsGetRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionsGetRequestOptions
import net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class BatchSyncRunner(
    @Value("\${fireflyPlaidConnector2.maxSyncDays}")
    private val syncDays: Int,

    @Value("\${fireflyPlaidConnector2.firefly.personalAccessToken}")
    private val fireflyAccessToken: String,
    private val fireflyTxApi: TransactionsApi,

    private val plaidApi: PlaidApi,
    @Value("\${fireflyPlaidConnector2.plaid.clientId}")
    private val plaidClientId: String,
    @Value("\${fireflyPlaidConnector2.plaid.secret}")
    private val plaidSecret: String,
    private val plaidAccountsConfig: AccountConfigs,

    private val converter: TransactionConverter,

    ) : Runner {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun run() {
        // Spring components are singletons by default, so this should set these credentials for any other
        //  component that also uses the plaidApi component
        plaidApi.setApiKey(plaidClientId, clientIdHeader)
        plaidApi.setApiKey(plaidSecret, secretHeader)
        fireflyTxApi.setAccessToken(fireflyAccessToken)

        val accountMap = plaidAccountsConfig.accounts.associate { Pair(it.plaidAccountId, it.fireflyAccountId) }
        val accountsByAccessToken = plaidAccountsConfig.accounts.groupBy { it.plaidItemAccessToken }
        val allPlaidTxs = mutableListOf<Transaction>()
        val plaidBatchSize = 100

        val startDate = LocalDate.now().minusDays(syncDays.toLong())
        val endDate = LocalDate.now()

        runBlocking {
            for ((accessToken, accountConfigs) in accountsByAccessToken) {
                val accountIds = accountConfigs.map { it.plaidAccountId }
                var offset = 0
                do {
                    /**
                     * Iterate through batches of Plaid transactions
                     *
                     * We're storing all this data in memory so we can try to match up offsetting transfers before inserting
                     *  into Firefly.
                     * Note that the heap size may need to be increased if you're handling a ton of transactions.
                     */
                    val request = TransactionsGetRequest(
                        accessToken,
                        startDate,
                        endDate,
                        null,
                        TransactionsGetRequestOptions(
                            accountIds,
                            plaidBatchSize,
                            offset,
                            includeOriginalDescription = true,
                            includePersonalFinanceCategoryBeta = false,
                            includePersonalFinanceCategory = true,
                        )
                    )
                    val plaidTxs: List<Transaction>
                    try {
                        plaidTxs = plaidApi.transactionsGet(request).body().transactions
                    } catch (cre: ClientRequestException) {
                        logger.error("Error requesting Plaid transactions. Request: $request; ")
                        throw cre
                    }
                    allPlaidTxs.addAll(plaidTxs)

                    /**
                     * This would be where we query transactions from Firefly and look for dupes, but the Firefly
                     *  API doesn't have a way to query by external id and I don't think it's worth the effort to
                     *  do date range queries and sift through all transactions, so for now we'll rely on Firefly's
                     *  "duplicate hash" dupe checking mechanism.
                     */

                    offset += plaidTxs.size

                    // Keep going until we get all the transactions
                } while (plaidTxs.size == plaidBatchSize)
            }

            // Map Plaid transactions to Firefly transactions
            val fireflyTxs = converter.convertBatch(allPlaidTxs, accountMap)

            // Insert into Firefly
//            logger.info("Inserting transactions: ${fireflyTxs} ${fireflyTxs.hashCode()}")
            fireflyTxApi.setAccessToken(fireflyAccessToken)
            for (fireflyTx in fireflyTxs) {
                try {
                    fireflyTxApi.storeTransaction(
                        TransactionStore(
                            fireflyTx.transactions,
                            true,
                            true,
                            true,
                            null,
                        )
                    )
                } catch (cre: ClientRequestException) {
                    if (cre.response.status == HttpStatusCode.UnprocessableEntity) {
                        val error = cre.response.body<FireflyApiError>()
                        if (error.message.lowercase().contains("duplicate of transaction")) {
                            logger.info("Skipped transaction ${fireflyTx.transactions.first().externalId} that Firefly identified as a duplicate")
                        } else {
                            logger.error("Firefly API error $error")
                            throw cre
                        }
                    } else {
                        throw cre
                    }
                }
            }
        }
    }
}