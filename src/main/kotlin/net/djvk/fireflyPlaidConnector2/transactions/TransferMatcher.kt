package net.djvk.fireflyPlaidConnector2.transactions

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.ZoneId
import kotlin.math.abs

/**
 * Identifies transaction pairs that represent a transfer from one account into another.
 */
@Component
class TransferMatcher(
    @Value("\${fireflyPlaidConnector2.timeZone}")
    private val timeZoneString: String,

    @Value("\${fireflyPlaidConnector2.transferMatchWindowDays}")
    private val transferMatchWindowDays: Long,
) {
    private val zoneId = ZoneId.of(timeZoneString)
    private val transferMatchWindowSeconds = transferMatchWindowDays * 24 * 60 * 60
    private val logger = LoggerFactory.getLogger(this::class.java)

    val transferTypes =
        setOf(
            PersonalFinanceCategoryEnum.Primary.TRANSFER_IN,
            PersonalFinanceCategoryEnum.Primary.TRANSFER_OUT,
            PersonalFinanceCategoryEnum.Primary.LOAN_PAYMENTS,
            PersonalFinanceCategoryEnum.Primary.BANK_FEES,
        )

    data class SortByPairsResult(
        val singles: List<PlaidFireflyTransaction>,
        val pairs: List<Pair<PlaidFireflyTransaction, PlaidFireflyTransaction>>,
    )

    /**
     * Identify matching transaction pairs that can be converted to a single "transfer" in Firefly.
     *
     * Note that this method will not perform any filtering. The caller is expected to filter-out any transactions
     * that it would not make sense to act on, such as matching pairs of Firefly transactions that do not have
     * corresponding Plaid transactions.
     */
    fun match(txs: List<PlaidFireflyTransaction>): SortByPairsResult {
        logger.trace("Starting ${::match.name}")

        // Split-out the transactions that are unlikely to be transfers based on their category. If we're not sure,
        // we'll try to match it as a transfer anyway.
        val (possibleTransfers, nonTransfers) = txs.partition {
            val category = it.plaidTransaction?.personalFinanceCategory
            category == null || transferTypes.contains(PersonalFinanceCategoryEnum.from(category).primary)
        }

        val pairsOut = mutableListOf<Pair<PlaidFireflyTransaction, PlaidFireflyTransaction>>()
        val singlesOut = nonTransfers.toMutableList()

        val amountIndexedTxs = possibleTransfers.groupBy { it.amount }
        // The loop below will process an amount value and its inverse, so we use this to mark the inverse
        //  as processed so we don't double process amount sets
        val processedAmounts = mutableSetOf<Double>()

        for ((amount, groupTxs) in amountIndexedTxs) {
            if (processedAmounts.contains(amount)) {
                continue
            }
            logger.trace("${::match.name} processing amount $amount with ${groupTxs.size} transactions")

            // Get all transfer txs that all have a currency amount inverse to groupTxs, which should theoretically be matching txs
            val matchingGroupTxs = amountIndexedTxs[-amount]
            processedAmounts.add(-amount)

            // If there are no matching txs, then this group has no soulmates and we should move on
            if (matchingGroupTxs == null) {
                singlesOut.addAll(groupTxs)
                continue
            }
            val txsSecondsDiff = mutableListOf<CandidatePair>()

            // Index txs by their temporal difference from each other so we can match up the closest pairs
            logger.trace("${::match.name} indexing transactions by time diff for amount $amount")
            for (aTx in groupTxs) {
                for (bTx in matchingGroupTxs) {
                    txsSecondsDiff.add(
                        CandidatePair(
                            abs(
                                aTx.getTimestamp(zoneId).toEpochSecond() -
                                        bTx.getTimestamp(zoneId).toEpochSecond()
                            ),
                            aTx,
                            bTx
                        )
                    )
                }
            }

            val sortedPairs = txsSecondsDiff
                .filter { it.secondsDiff < transferMatchWindowSeconds }
                .sortedBy { it.secondsDiff }

            /**
             * Ids of transactions we've already used from either group so we don't use them again.
             * This is an issue because the [CandidatePair] array we make matches every A transaction to every B
             *  transaction, so each transaction appears more than once in the array.
             */
            val usedATxIds = mutableSetOf<String>()
            val usedBTxIds = mutableSetOf<String>()

            sortedPairs.filter { (_, aTx, bTx) ->
                // Skip any pairs where we've already successfully matched one of the transactions
                val match = !usedATxIds.contains(aTx.transactionId)
                        && !usedBTxIds.contains(bTx.transactionId)

                        // The transfers we're looking for are from one account to another; skip any pairs where both
                        // transactions are for the same account.
                        && aTx.fireflyAccountId != bTx.fireflyAccountId

                if (match) {
                    usedATxIds.add(aTx.transactionId)
                    usedBTxIds.add(bTx.transactionId)
                }

                match
            }.map { (_, aTx, bTx) ->
                logger.trace("${::match.name} found valid pair with timestamps ${aTx.getTimestamp(zoneId)};" +
                        "${bTx.getTimestamp(zoneId)} and amount $amount")
                pairsOut.add(Pair(aTx, bTx))
            }

            // Output all leftover transactions as singles
            singlesOut.addAll(groupTxs.filter { !usedATxIds.contains(it.transactionId) })
            singlesOut.addAll(matchingGroupTxs.filter { !usedBTxIds.contains(it.transactionId) })
        }

        return SortByPairsResult(singlesOut, pairsOut)
    }

    private data class CandidatePair(
        val secondsDiff: Long,
        val aTx: PlaidFireflyTransaction,
        val bTx: PlaidFireflyTransaction,
    )
}
