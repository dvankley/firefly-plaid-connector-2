package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategoryEnum
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategoryEnum.Primary.*
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PlaidTransactionId
import net.djvk.fireflyPlaidConnector2.constants.Direction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.*
import java.util.*
import kotlin.math.abs
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

typealias PlaidAccountId = String
typealias FireflyAccountId = Int

@Component
class TransactionConverter(
    @Value("\${fireflyPlaidConnector2.useNameForDestination:true}")
    private val useNameForDestination: Boolean,
    @Value("\${fireflyPlaidConnector2.timeZone}")
    private val timeZoneString: String,
    @Value("\${fireflyPlaidConnector2.transferMatchWindowDays}")
    private val transferMatchWindowDays: Long,

    @Value("\${fireflyPlaidConnector2.categorization.primary.enable:false}")
    private val enablePrimaryCategorization: Boolean,
    @Value("\${fireflyPlaidConnector2.categorization.primary.prefix:plaid-primary-cat-}")
    private val primaryCategoryPrefix: String,

    @Value("\${fireflyPlaidConnector2.categorization.detailed.enable:false}")
    private val enableDetailedCategorization: Boolean,
    @Value("\${fireflyPlaidConnector2.categorization.detailed.prefix:plaid-detailed-cat-}")
    private val detailedCategoryPrefix: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val timeZone = TimeZone.getTimeZone(timeZoneString)
    private val transferMatchWindowSeconds = transferMatchWindowDays * 24 * 60 * 60

    companion object {
        fun convertScreamingSnakeCaseToKebabCase(input: String): String {
            return input
                .replace("_", "-")
                .lowercase()
        }

        fun getOffsetDateTimeForDate(zoneId: ZoneId, date: LocalDate): OffsetDateTime {
            val instant = date.atStartOfDay(zoneId).toInstant()
            val offset = zoneId.rules.getOffset(instant)
            return date.atTime(OffsetTime.of(0, 0, 0, 0, offset))
        }

        /**
         * Basically the inverse of [getFireflyTransactionDtoType]
         */
        fun getPlaidAmount(tx: FireflyTransactionDto): Double {
            return when (tx.tx.type) {
                TransactionTypeProperty.withdrawal -> tx.tx.amount.toDouble()
                TransactionTypeProperty.deposit -> -tx.tx.amount.toDouble()
                else -> throw IllegalArgumentException("Can't get Plaid amount for a Firefly transaction of type ${tx.tx.type}")
            }
        }
    }

    fun getTxTimestamp(tx: PlaidTransaction): OffsetDateTime {
        return tx.datetime
            ?: tx.authorizedDatetime
            ?: getOffsetDateTimeForDate(timeZone.toZoneId(), tx.date)
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
     * Convert a batch of Plaid transactions to Firefly transactions in batch syncing mode, as opposed to [convertPollSync]
     *
     * A batch should include the widest date range possible and should include transactions from
     *  all available Plaid accounts to enable matching of transfer transactions.
     */
    suspend fun convertBatchSync(
        txs: List<PlaidTransaction>,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): List<FireflyTransactionDto> {
        val (singles, pairs) = sortByPairsBatched(txs, accountMap)
        val out = mutableListOf<FireflyTransactionDto>()

        for (single in singles) {
            out.add(convertSingle(single, accountMap))
        }

        for (pair in pairs) {
            out.add(convertDoublePlaid(pair.first, pair.second, accountMap))
        }

        return out
    }

    data class ConvertPollSyncResult(
        val creates: List<FireflyTransactionDto>,
        val updates: List<FireflyTransactionDto>,
        val deletes: List<FireflyTransactionId>,
    )

    /**
     * Convert a batch of Plaid change events and existing Firefly transactions to Firefly creates, updates, and
     *  deletes in poll syncing mode, as opposed to [convertBatchSync]
     *
     * [existingFireflyTxs] should include transactions from fireflyPlaidConnector2.transferMatchWindowDays ago for
     *  transfer matching purposes.
     */
    suspend fun convertPollSync(
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        plaidCreatedTxs: List<PlaidTransaction>,
        plaidUpdatedTxs: List<PlaidTransaction>,
        plaidDeletedTxs: List<PlaidTransactionId>,
        existingFireflyTxs: List<FireflyTransactionDto>,
    ): ConvertPollSyncResult {
        logger.trace("Starting ${::convertPollSync.name}")
        val createdSet = plaidCreatedTxs.toSet()
        val updatedSet = plaidUpdatedTxs.toSet()

        val creates = mutableListOf<FireflyTransactionDto>()
        val updates = mutableListOf<FireflyTransactionDto>()
        val deletes = mutableListOf<FireflyTransactionId>()

        val (singles, pairs) = sortByPairs(plaidCreatedTxs + plaidUpdatedTxs + existingFireflyTxs, accountMap)

        for (single in singles) {
            val convertedSingle = when (single) {
                is PlaidTransaction -> convertSingle(single, accountMap)
                is FireflyTransactionDto -> single
                else -> throw RuntimeException("Failed to convert transaction of type ${single::class} in poll sync")
            }

            if (convertedSingle.id == null) {
                creates.add(convertedSingle)
            } else {
                updates.add(convertedSingle)
            }
        }

        for (pair in pairs) {
            val out = when {
                pair.first is FireflyTransactionDto && pair.second is FireflyTransactionDto ->
                    throw IllegalArgumentException("Sorted pair illegally has two Firefly transactions: $pair")

                pair.first is FireflyTransactionDto || pair.second is FireflyTransactionDto -> {
                    val (plaid, firefly) = if (pair.first is FireflyTransactionDto) Pair(
                        pair.second,
                        pair.first
                    ) else pair
                    convertDoubleFirefly(
                        plaid as PlaidTransaction,
                        firefly as FireflyTransactionDto,
                        accountMap,
                    )
                }

                pair.first is PlaidTransaction && pair.second is PlaidTransaction -> {
                    convertDoublePlaid(
                        pair.first as PlaidTransaction,
                        pair.second as PlaidTransaction,
                        accountMap,
                    )
                }

                else -> throw IllegalArgumentException("Unexpected sorted pair state: $pair")
            }
            val plaidComponent = when {
                pair.first is PlaidTransaction -> pair.first
                pair.second is PlaidTransaction -> pair.second
                else -> throw IllegalArgumentException("Sorted pair illegally has two Firefly transactions: $pair")
            } as PlaidTransaction

            if (out.id != null) {
                updates.add(out)
            } else if (createdSet.contains(plaidComponent)) {
                // TODO: what happens if the pair is two Plaid transactions, one create and one update?
                creates.add(out)
            } else if (updatedSet.contains(plaidComponent)) {
                updates.add(out)
            } else {
                throw IllegalArgumentException("Unable to determine create/update status of sorted pair: $pair")
            }
        }

        return ConvertPollSyncResult(
            creates = creates,
            updates = updates,
            deletes = deletes,
        )
    }

    data class SortByPairsBatchedResult(
        val singles: List<PlaidTransaction>,
        val pairs: List<Pair<PlaidTransaction, PlaidTransaction>>,
    )

    suspend fun sortByPairsBatched(
        txs: List<PlaidTransaction>,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): SortByPairsBatchedResult {
        // Split Plaid transactions based on whether they are transfers or not
        val (transfers, nonTransfers) = txs.partition {
            transferTypes.contains(it.personalFinanceCategory.toEnum().primary)
        }
        val (singles, pairs) = sortByPairs(transfers, accountMap)

        return SortByPairsBatchedResult(
            singles as List<PlaidTransaction> + nonTransfers,
            pairs as List<Pair<PlaidTransaction, PlaidTransaction>>,
        )
    }

    data class CandidatePair(
        val secondsDiff: Long,
        val aTx: SortableTransaction,
        val bTx: SortableTransaction,
    )

    val transferTypes =
        setOf(
            PersonalFinanceCategoryEnum.Primary.TRANSFER_IN,
            PersonalFinanceCategoryEnum.Primary.TRANSFER_OUT,
            PersonalFinanceCategoryEnum.Primary.LOAN_PAYMENTS,
            PersonalFinanceCategoryEnum.Primary.BANK_FEES,
        )

    data class SortByPairsResult(
        val singles: List<SortableTransaction>,
        val pairs: List<Pair<SortableTransaction, SortableTransaction>>,
    )

    /**
     * Attempt to find pairs of Plaid and/or Firefly transactions that make up one actual transfer transaction
     *
     * Only visible for testing
     */
    protected suspend fun sortByPairs(
        txs: List<SortableTransaction>,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): SortByPairsResult {
        logger.trace("Starting ${::sortByPairs.name}")
        val pairsOut = mutableListOf<Pair<SortableTransaction, SortableTransaction>>()
        val singlesOut = mutableListOf<SortableTransaction>()

        val amountIndexedTxs = txs.groupBy { it.amount }
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
                            abs(
                                aTx.getTimestamp(timeZone.toZoneId()).toEpochSecond() -
                                        bTx.getTimestamp(timeZone.toZoneId()).toEpochSecond()
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
            val usedATxIds = mutableSetOf<PlaidTransactionId>()
            val usedBTxIds = mutableSetOf<PlaidTransactionId>()

            for ((_, aTx, bTx) in sortedPairs) {
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

                /**
                 * Check if one and only one tx is Firefly
                 * If one and only one tx is Firefly, this is a candidate for updating the existing Firefly transaction
                 *  to a transfer.
                 */
                val (fireflyTx, plaidTx) = if (aTx is FireflyTransactionDto && bTx is PlaidTransaction) {
                    Pair(aTx, bTx)
                } else if (aTx is PlaidTransaction && bTx is FireflyTransactionDto) {
                    Pair(bTx, aTx)
                } else if (aTx is FireflyTransactionDto && bTx is FireflyTransactionDto) {
                    // Transfer can't be composed of two Firefly transactions
                    continue
                } else {
                    // Two Plaid transactions, which is fine
                    Pair(null, null)
                }

                // Otherwise let's peel off the next pair of transactions
                pairsOut.add(Pair(aTx, bTx))
                usedATxIds.add(aTx.transactionId)
                usedBTxIds.add(bTx.transactionId)
            }
        }

        /**
         * Existing Firefly transactions are only used here to create transfers, so filter them out
         *  of the output
         */
        return SortByPairsResult(singlesOut.filter { it !is FireflyTransactionDto }, pairsOut)
    }

    protected suspend fun convertSingle(
        tx: PlaidTransaction,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): FireflyTransactionDto {
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

    protected suspend fun convertDoublePlaid(
        a: PlaidTransaction,
        b: PlaidTransaction,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): FireflyTransactionDto {
        val (sourceTx, destinationTx) = if (a.amount < 0.0) Pair(b, a) else Pair(a, b)
        return convert(
            // The destination transaction tends to have the most relevant categorization information
            tx = destinationTx,
            isPair = true,
            sourceId = accountMap[sourceTx.accountId]?.toString()
                ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${sourceTx.accountId}"),
            destinationId = accountMap[destinationTx.accountId]?.toString()
                ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${destinationTx.accountId}"),
        )
    }

    protected suspend fun convertDoubleFirefly(
        plaidTx: PlaidTransaction,
        fireflyTx: FireflyTransactionDto,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
    ): FireflyTransactionDto {
        val plaidTxFireflyAccountId = accountMap[plaidTx.accountId]
            ?: throw RuntimeException("Failed to find Firefly account mapping for Plaid account ${plaidTx.accountId}")

        /**
         * With transfers, the two component transactions are the inverse of each other.
         * Since we know the transaction amounts have opposite signs, we know, in Firefly terms, one is a deposit
         *  and one is a withdrawal.
         *
         * For Plaid transactions, we can only be confident in the accuracy of the account the transaction is
         *  "on," the rest is just based on the name field, which isn't super accurate.
         * For Firefly transactions, it depends;
         * - Withdrawal: positive Plaid value. The Firefly tx would have the source account right, and the
         *  destination account would be garbage.
         * - Deposit: negative Plaid value. The Firefly tx would have the destination account right, and the
         *  source account would be garbage.
         */
        val sourceId: String?
        val sourceName: String?
        val destinationId: String?
        val destinationName: String?
        when (fireflyTx.tx.type) {
            TransactionTypeProperty.withdrawal -> {
                sourceName = fireflyTx.tx.sourceName
                sourceId = null

                destinationName = null
                destinationId = plaidTxFireflyAccountId.toString()
            }

            TransactionTypeProperty.deposit -> {
                sourceName = null
                sourceId = plaidTxFireflyAccountId.toString()

                destinationName = fireflyTx.tx.destinationName
                destinationId = null
            }

            else -> throw IllegalArgumentException(
                "Unable to convert an existing Firefly transaction of type " +
                        "${fireflyTx.tx.type} to a transfer."
            )
        }

        return convert(
            tx = plaidTx,
            isPair = true,
            sourceId = sourceId,
            sourceName = sourceName,
            destinationId = destinationId,
            destinationName = destinationName,
            fireflyTx = fireflyTx,
        )
    }

    /**
     * @param fireflyTx Only included in cases where we're converting a Plaid/Firefly tx pair into a transfer.
     */
    protected suspend fun convert(
        tx: PlaidTransaction,
        isPair: Boolean,
        sourceId: String? = null,
        sourceName: String? = null,
        destinationId: String? = null,
        destinationName: String? = null,
        fireflyTx: FireflyTransactionDto? = null,
    ): FireflyTransactionDto {
        val timestamp = getTxTimestamp(tx)
        val split = TransactionSplit(
            getFireflyTransactionDtoType(tx, isPair),
            timestamp,
            /**
             * Always positive per https://github.com/firefly-iii/firefly-iii/issues/2476
             * "Direction" of transactions handled in [getFireflyTransactionDtoType]
             */
            abs(tx.amount).toString(),
            fireflyTx?.tx?.description
                ?: (tx.name +
                        if (tx.originalDescription == null) {
                            ""
                        } else {
                            ": ${tx.originalDescription}"
                        }),
            sourceId = sourceId,
            sourceName = sourceName,
            destinationId = destinationId,
            destinationName = destinationName,
            tags = getFireflyCategoryTags(tx),
            externalId = getExternalId(tx),
            order = 0,
            reconciled = false,
            // Why the eff does the Firefly API require this
            foreignAmount = "0",
            // These are all explicitly required, but only for updates
            currencyId = fireflyTx?.tx?.currencyId,
            currencyCode = fireflyTx?.tx?.currencyCode,
        )
        return FireflyTransactionDto(
            fireflyTx?.transactionId,
            split,
        )
    }

    /**
     * This is our primary mechanism for enabling the use of Firefly's budgets and categories.
     * The intent is to use this functionality to tag Firefly transactions with Plaid category data,
     *  then use those tags with Firefly's rule engine to apply categories and budgets as desired.
     */
    protected suspend fun getFireflyCategoryTags(tx: PlaidTransaction): List<String> {
        val tagz = mutableListOf<String>()
        if (enablePrimaryCategorization) {
            tagz.add(
                primaryCategoryPrefix +
                        convertScreamingSnakeCaseToKebabCase(tx.personalFinanceCategory.primary)
            )
        }
        if (enableDetailedCategorization) {
            tagz.add(
                detailedCategoryPrefix +
                        convertScreamingSnakeCaseToKebabCase(tx.personalFinanceCategory.toEnum().detailed.name)
            )
        }
        return tagz
    }

    /**
     * [Firefly transaction types](https://docs.firefly-iii.org/firefly-iii/support/transaction_types/)
     * See [getPlaidAmount] for sort of the inverse of this.
     *
     * @param isPair True if [t] is part of a pair of offsetting Plaid transactions, false otherwise.
     */
    suspend fun getFireflyTransactionDtoType(t: PlaidTransaction, isPair: Boolean): TransactionTypeProperty {

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