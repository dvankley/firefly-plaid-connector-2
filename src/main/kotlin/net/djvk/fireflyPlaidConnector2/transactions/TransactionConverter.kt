package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategoryEnum
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategoryEnum.Primary.*
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PlaidTransactionId
import net.djvk.fireflyPlaidConnector2.constants.Direction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import kotlin.math.abs
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Transaction as FireflyTransaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

typealias PlaidAccountId = String
typealias FireflyAccountId = Int

@Component
class TransactionConverter(
    @Value("\${fireflyPlaidConnector2.useNameForDestination:true}")
    private val useNameForDestination: Boolean,

    @Value("\${fireflyPlaidConnector2.categorization.primary.enable:false}")
    private val enablePrimaryCategorization: Boolean,
    @Value("\${fireflyPlaidConnector2.categorization.primary.prefix:plaid-primary-cat-}")
    private val primaryCategoryPrefix: String,

    @Value("\${fireflyPlaidConnector2.categorization.detailed.enable:false}")
    private val enableDetailedCategorization: Boolean,
    @Value("\${fireflyPlaidConnector2.categorization.primary.prefix:plaid-detailed-cat-}")
    private val detailedCategoryPrefix: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        fun getTxTimestamp(tx: PlaidTransaction): OffsetDateTime {
            return tx.datetime
                ?: tx.authorizedDatetime
                // We're using a UTC zone here because the value we're given is only a date
                ?: tx.date.atTime(OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC))
        }
    }

    fun getSourceOrDestinationName(
        tx: PlaidTransaction,
        isSource: Boolean,
    ): String {
        return tx.merchantName
            ?: if (useNameForDestination) {
                tx.name.take(255)
            } else {
                getUnknownSourceOrDestinationName(tx.personalFinanceCategory.toEnum(), isSource)
            }
    }

    /**
     * Gets the name to use for an external account in cases where we don't have any better info
     * @param isSource True if source, false if destination
     */
    fun getUnknownSourceOrDestinationName(
        pfc: PersonalFinanceCategoryEnum,
        isSource: Boolean,
    ): String {
        val typeString = when (pfc.primary) {
            INCOME -> "Income"
            TRANSFER_IN, TRANSFER_OUT -> "Transfer"
            LOAN_PAYMENTS, BANK_FEES, ENTERTAINMENT, FOOD_AND_DRINK, GENERAL_MERCHANDISE, HOME_IMPROVEMENT,
            MEDICAL, PERSONAL_CARE, GENERAL_SERVICES, GOVERNMENT_AND_NON_PROFIT, TRANSPORTATION, TRAVEL,
            RENT_AND_UTILITIES -> "Payment"
        }
        val sourceString = if (isSource) {
            "Source"
        } else {
            "Recipient"
        }
        return "Unknown $typeString $sourceString"
    }

    fun getExternalId(tx: PlaidTransaction): String {
        return "plaid-${tx.transactionId}"
    }

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

    data class CandidatePair(
        val secondsDiff: Long,
        val aTx: PlaidTransaction,
        val bTx: PlaidTransaction,
    )

    val transferTypes =
        setOf(
            PersonalFinanceCategoryEnum.Primary.TRANSFER_IN,
            PersonalFinanceCategoryEnum.Primary.TRANSFER_OUT,
            PersonalFinanceCategoryEnum.Primary.LOAN_PAYMENTS,
            PersonalFinanceCategoryEnum.Primary.BANK_FEES,
        )

    /**
     * Attempt to find pairs of Plaid transactions that make up one actual transfer transaction
     *
     * Only visible for testing
     */
    suspend fun sortByPairs(txs: List<PlaidTransaction>):
            Pair<List<PlaidTransaction>, List<Pair<PlaidTransaction, PlaidTransaction>>> {
        // Split Plaid transactions based on whether they are transfers or not
        val (transfers, nonTransfers) = txs.partition {
            transferTypes.contains(it.personalFinanceCategory.toEnum().primary)
        }
        val pairsOut = mutableListOf<Pair<PlaidTransaction, PlaidTransaction>>()
        val singlesOut = nonTransfers.toMutableList()

        val amountIndexedTxs = transfers
            .groupBy { it.amount }
        // The loop below will process an amount value and its inverse, so we use this to mark the inverse
        //  as processed so we don't double process amount sets
        val processedAmounts = mutableSetOf<Double>()

        for ((amount, groupTxs) in amountIndexedTxs) {
            if (processedAmounts.contains(amount)) {
                continue
            }
            // Get all transfer txs that all have a currency amount inverse to groupTxs, which should theoretically be matching txs
            val matchingGroupTxs = amountIndexedTxs[-amount]
            processedAmounts.add(-amount)

            // If there are no matching txs, then this group has no soulmates and we should move on
            if (matchingGroupTxs == null) {
                singlesOut.addAll(groupTxs)
                continue
            }
            val aTxs = groupTxs
            val bTxs = matchingGroupTxs
            val aTxIds = aTxs.map { it.transactionId }.toHashSet()
            val bTxIds = bTxs.map { it.transactionId }.toHashSet()
            val txsSecondsDiff = mutableListOf<CandidatePair>()

            // Index txs by their temporal difference from each other so we can match up the closest pairs
            for (aTx in aTxs) {
                for (bTx in bTxs) {
                    txsSecondsDiff.add(
                        CandidatePair(
                            abs(getTxTimestamp(aTx).toEpochSecond() - getTxTimestamp(bTx).toEpochSecond()),
                            aTx,
                            bTx
                        )
                    )
                }
            }

            val sortedPairs = txsSecondsDiff.sortedBy { it.secondsDiff }

            /**
             * Ids of transactions we've already used from either group so we don't use them again.
             * This is an issue because the [CandidatePair] array we make matches every A transaction to every B
             *  transaction, so each transaction appears more than once in the array.
             */
            val usedATxIds = mutableSetOf<PlaidTransactionId>()
            val usedBTxIds = mutableSetOf<PlaidTransactionId>()

            for ((diff, aTx, bTx) in sortedPairs) {
                // If we don't have any remaining possible transactions in the input sets, then we're done here
                if ((aTxIds.size - usedATxIds.size) < 1 ||
                    (bTxIds.size - usedBTxIds.size) < 1
                ) {
                    // Output all leftover transactions as singles
                    singlesOut.addAll(aTxs.filter { !usedATxIds.contains(it.transactionId) })
                    singlesOut.addAll(bTxs.filter { !usedBTxIds.contains(it.transactionId) })
                    break
                }
                // If we already used either transaction A or B, then move on to the next candidate
                if (usedATxIds.contains(aTx.transactionId) || usedBTxIds.contains(bTx.transactionId)) {
                    continue
                }

                // Otherwise let's peel off the next pair of transactions
                pairsOut.add(Pair(aTx, bTx))
                usedATxIds.add(aTx.transactionId)
                usedBTxIds.add(bTx.transactionId)
            }
        }

        return Pair(singlesOut, pairsOut)
    }

    protected fun getSourceKey(amount: Double, source: String?): String {
        return "${amount}|${source}"
    }

    protected suspend fun convertSingle(
        tx: PlaidTransaction,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): FireflyTransaction {
        val fireflyAccountId = accountMap[tx.accountId]?.toString()
            ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${tx.accountId}")

        val sourceId: String?
        val sourceName: String?
        val destinationId: String?
        val destinationName: String?
        if (tx.getDirection() == Direction.IN) {
            destinationId = fireflyAccountId
            destinationName = null

            sourceId = null
            sourceName = getSourceOrDestinationName(tx, true)
        } else {
            sourceId = fireflyAccountId
            sourceName = null

            destinationId = null
            destinationName = getSourceOrDestinationName(tx, false)
        }
        return convert(
            tx = tx,
            isPair = false,
            sourceId = sourceId,
            sourceName = sourceName,
            destinationId = destinationId,
            destinationName = destinationName,
        )
    }

    protected suspend fun convertDouble(
        a: PlaidTransaction,
        b: PlaidTransaction,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): FireflyTransaction {
        return convert(
            tx = a,
            isPair = true,
            sourceId = accountMap[b.accountId]?.toString()
                ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${b.accountId}"),
            destinationId = accountMap[a.accountId]?.toString()
                ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${a.accountId}"),
        )
    }

    protected suspend fun convert(
        tx: PlaidTransaction,
        isPair: Boolean,
        sourceId: String? = null,
        sourceName: String? = null,
        destinationId: String? = null,
        destinationName: String? = null,
    ): FireflyTransaction {
        // TODO: categories
        val timestamp = getTxTimestamp(tx)
        val split = TransactionSplit(
            getFireflyTransactionType(tx, isPair),
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
            sourceId = sourceId,
            sourceName = sourceName,
            destinationId = destinationId,
            destinationName = destinationName,
            externalId = getExternalId(tx),
            order = 0,
            // Why the eff does the Firefly API require this
            foreignAmount = "0",
            reconciled = false,
        )
        return FireflyTransaction(
            listOf(split),
            timestamp,
        )
    }

    /**
     * [Firefly transaction types](https://docs.firefly-iii.org/firefly-iii/support/transaction_types/)
     *
     * @param isPair True if [t] is part of a pair of offsetting Plaid transactions, false otherwise.
     */
    suspend fun getFireflyTransactionType(t: PlaidTransaction, isPair: Boolean): TransactionTypeProperty {

        /**
         * Per Firefly docs:
         * Transfers are internal transactions that don't influence your bottom line.
         * A transfer is created only between existing asset accounts.
         * Select an asset account for both the source and destination from the free-form fields.
         * Transfers can be linked to piggy banks, to automatically add or remove money from the piggy bank you select.
         */
        if (isPair) {
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