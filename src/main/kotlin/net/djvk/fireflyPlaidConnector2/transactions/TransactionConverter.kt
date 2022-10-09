package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.categories.PlaidOldCategoryCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.OffsetTime
import java.time.ZoneOffset
import kotlin.math.abs
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Transaction as FireflyTransaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

typealias PlaidAccountId = String
typealias FireflyAccountId = Int

@Component
class TransactionConverter(
    private val categoryCache: PlaidOldCategoryCache,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Convert a batch of Plaid transactions to Firefly transactions
     *
     * Batch boundaries should be based on transaction date and should include transactions from
     *  all available Plaid accounts to enable matching of transfer transactions.
     */
    suspend fun convertBatch(
        txs: List<PlaidTransaction>,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): List<FireflyTransaction> {
        val (singles, pairs) = sortByPairs(txs)
        val out = mutableListOf<FireflyTransaction>()

        for (single in singles) {
            out.add(convertSingle(single, accountMap))
        }

        for (pair in pairs) {
            out.add(convertDouble(pair.first, pair.second, accountMap))
        }

        return out
    }

    /**
     * Attempt to find pairs of Plaid transactions that make up one actual transfer transaction
     */
    protected suspend fun sortByPairs(txs: List<PlaidTransaction>):
            Pair<List<PlaidTransaction>, List<Pair<PlaidTransaction, PlaidTransaction>>> {
        // Split Plaid transactions based on whether they are transfers or not
        val (nonTransfers, transfers) = txs.partition { it.paymentMeta.payee == null }

        val pairsOut = mutableListOf<Pair<PlaidTransaction, PlaidTransaction>>()
        val singlesOut = nonTransfers.toMutableList()
        val remainingTxs = transfers.toMutableSet()

        val sourceIndexedTxs = transfers
            .associateBy { getSourceKey(it.amount, it.paymentMeta.payer) }
            .toMutableMap()
//        val destIndexedTxs = transfers.associateBy { getDestinationKey(it) }.toMutableMap()

        for (tx in remainingTxs) {
            val sourceKey = getSourceKey(tx.amount, tx.paymentMeta.payer)
            if (!sourceIndexedTxs.contains(sourceKey)) {
                // If this transaction isn't in the map of indexed transactions, it was already removed
                //  earlier as part of a pair with another transaction, so we should move on
                continue
            }
            // Negate amount and switch payer and payee to search for matching transaction
            val pairSourceKey = getSourceKey(-tx.amount, tx.paymentMeta.payee)
            val pairMatch = sourceIndexedTxs[pairSourceKey]
            if (pairMatch != null) {
                // Add this pair to output
                pairsOut.add(Pair(tx, pairMatch))
                // Remove both Plaid transactions
                sourceIndexedTxs.remove(sourceKey)
                sourceIndexedTxs.remove(pairSourceKey)
            }
        }

        return Pair(singlesOut + sourceIndexedTxs.values, pairsOut)
    }

    protected fun getSourceKey(amount: Double, source: String?): String {
        return "${amount}|${source}"
    }

//    protected fun getDestinationKey(tx: PlaidTransaction): String {
//        return "${tx.amount}|${tx.paymentMeta.payee}"
//    }

    protected suspend fun convertSingle(
        tx: PlaidTransaction,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): FireflyTransaction {

        // TODO: categories
        val timestamp = tx.datetime
            ?: tx.authorizedDatetime
            ?: tx.datetime
            // We're using a UTC zone here because the value we're given is only a date
            ?: tx.date.atTime(OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC))
        val split = TransactionSplit(
            getFireflyTransactionType(tx),
            timestamp,
            /**
             * Always positive per https://github.com/firefly-iii/firefly-iii/issues/2476
             * "Direction" of transactions handled in [getFireflyTransactionType]
             */
            abs(tx.amount).toString(),
            tx.name +
                    if (tx.originalDescription == null) {
                        ""
                    } else {
                        ": ${tx.originalDescription}"
                    },
            sourceId = null,
            sourceName = tx.merchantName,
            destinationId = accountMap[tx.accountId]?.toString()
                ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${tx.accountId}"),
        )
        return FireflyTransaction(
            listOf(split),
            timestamp,
        )
    }

    protected suspend fun convertDouble(
        a: PlaidTransaction,
        b: PlaidTransaction,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): FireflyTransaction {
        val timestamp = a.datetime
            ?: a.authorizedDatetime
            ?: a.datetime
            // We're using a UTC zone here because the value we're given is only a date
            ?: a.date.atTime(OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC))
        val split = TransactionSplit(
            getFireflyTransactionType(a),
            timestamp,
            /**
             * Always positive per https://github.com/firefly-iii/firefly-iii/issues/2476
             * "Direction" of transactions handled in [getFireflyTransactionType]
             */
            abs(a.amount).toString(),
            a.name +
                    if (a.originalDescription == null) {
                        ""
                    } else {
                        ": ${a.originalDescription}"
                    },
            sourceId = accountMap[b.accountId]?.toString()
                ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${b.accountId}"),
            destinationId = accountMap[a.accountId]?.toString()
                ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${a.accountId}"),
        )
        return FireflyTransaction(
            listOf(split),
            timestamp,
        )
    }

    /**
     * [Firefly transaction types](https://docs.firefly-iii.org/firefly-iii/support/transaction_types/)
     */
    suspend fun getFireflyTransactionType(t: PlaidTransaction): TransactionTypeProperty {
        /**
         * Per Firefly docs:
         * Transfers are internal transactions that don't influence your bottom line.
         *
         * Per Plaid docs:
         * "If the transaction was not an inter-bank transfer, all fields will be `null`"
         */
        if (t.paymentMeta.payee != null) {
            return TransactionTypeProperty.transfer
        }

        /**
         * Per Firefly docs:
         * Withdrawals represent money that you spent that you can't get back easily unless the receiving party sends it to you.
         * Deposits represent money that you received from others.
         *
         * Per Plaid docs on `amount`:
         * Positive values when money moves out of the account; negative values when money moves in. For example,
         *  debit card purchases are positive; credit card payments, direct deposits, and refunds are negative.
         */
        return if (t.amount > 0) {
            TransactionTypeProperty.withdrawal
        } else {
            return TransactionTypeProperty.deposit
        }
    }
}