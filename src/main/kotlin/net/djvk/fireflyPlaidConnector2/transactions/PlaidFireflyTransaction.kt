package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Represents a single non-transfer transaction, including both the Plaid and the Firefly representation of it.
 *
 * See the descriptions of the individual subtypes for more details about the situations in which each is used.
 */
sealed interface PlaidFireflyTransaction {
    val amount: Double
    val transactionId: String
    val fireflyAccountId: Int
    val fireflyTransaction: FireflyTransactionDto?
    val plaidTransaction: Transaction?
    fun getTimestamp(zoneId: ZoneId): OffsetDateTime

    companion object {
        /**
         * Normalizes Plaid and Firefly transactions into PlaidFireflyTransaction objects, joining them together as a
         * MatchedTransaction when a Plaid transaction has a transactionId that matches a Firefly transaction's
         * externalId.
         */
        fun normalizeByTransactionId(
            plaidTxs: List<Transaction>,
            fireflyTxs: List<FireflyTransactionDto>,
            accountMap: Map<String, Int>
        ): List<PlaidFireflyTransaction> {
            val plaidById = plaidTxs.groupBy { FireflyTransactionExternalIdIndexer.getExternalId(it.transactionId) }
            val fireflyByExtId = fireflyTxs.groupBy { it.tx.externalId }
            val externalIds = plaidById.keys.union(fireflyByExtId.keys)

            return externalIds.flatMap { externalId ->
                val matchingPlaid = plaidById[externalId] ?: listOf()
                val matchingFirefly = fireflyByExtId[externalId] ?: listOf()

                // For all transactions that do not have an external ID, or if we've found more matching transactions
                // than we expected to find, return them without attempting to combine.
                if (externalId == null || matchingFirefly.size > 1 || matchingPlaid.size > 1) {
                    val convertedFirefly = matchingFirefly.map { FireflyTransaction(it) }
                    val convertedPlaid = matchingPlaid.map { matchingPlaidTx ->
                        val accountId = accountMap[matchingPlaidTx.accountId]
                            ?: throw throw IllegalArgumentException("Can not match Plaid transactions from accounts not mapped "
                                    + "to a Firefly account id")
                        PlaidTransaction(matchingPlaidTx, accountId)
                    }
                    return@flatMap convertedPlaid + convertedFirefly
                }

                val plaidTx = matchingPlaid.getOrNull(0)
                val fireflyTx = matchingFirefly.getOrNull(0)

                val converted = if (plaidTx != null && fireflyTx != null) {
                    listOf(MatchedTransaction(plaidTx, fireflyTx))
                } else if (plaidTx != null) {
                    val accountId = accountMap[plaidTx.accountId]
                    if (accountId == null) {
                        throw throw IllegalArgumentException("Can not match Plaid transactions from accounts not mapped "
                                + "to a Firefly account id")
                    }
                    listOf(PlaidTransaction(plaidTx, accountId))
                } else if (fireflyTx != null) {
                    listOf(FireflyTransaction(fireflyTx))
                } else {
                    throw RuntimeException("Unexpected transaction combination")
                }

                converted
            }
        }

        fun getPlaidTransactionDate(tx: Transaction, zoneId: ZoneId): OffsetDateTime {
            return tx.datetime
                ?: tx.authorizedDatetime
                ?: TransactionConverter.getOffsetDateTimeForDate(zoneId, tx.date)
        }

        private fun getFireflyAccountId(dto: FireflyTransactionDto): Int {
            return when (dto.tx.type) {
                TransactionTypeProperty.deposit -> dto.tx.destinationId?.toInt()
                    ?: throw IllegalArgumentException("Firefly deposit ${dto.id} is missing the "
                            + "required destinationId field")
                TransactionTypeProperty.withdrawal -> dto.tx.sourceId?.toInt()
                    ?: throw IllegalArgumentException("Firefly withdrawal ${dto.id} is missing the "
                            + "required sourceId field")
                else -> throw IllegalArgumentException("Only withdrawal or deposit transaction types are supported")
            }
        }
    }

    /**
     * This subtype is used when we have a transaction from Plaid that either hasn't been created yet in Firefly, or
     * has been created but we haven't found/loaded it.
     */
    data class PlaidTransaction(
        override val plaidTransaction: Transaction,
        override val fireflyAccountId: Int,
    ): PlaidFireflyTransaction {
        override val amount = plaidTransaction.amount
        override val transactionId = plaidTransaction.transactionId
        override fun getTimestamp(zoneId: ZoneId): OffsetDateTime {
            return getPlaidTransactionDate(plaidTransaction, zoneId)
        }
        override val fireflyTransaction = null
    }

    /**
     * This subtype is used when we've loaded a transaction from Firefly that we haven't seen in the current set of
     * transactions from Plaid.
     */
    data class FireflyTransaction(override val fireflyTransaction: FireflyTransactionDto): PlaidFireflyTransaction {
        override val amount = fireflyTransaction.amount
        override val fireflyAccountId get() = getFireflyAccountId(fireflyTransaction)
        override val transactionId get() = fireflyTransaction.id ?: throw IllegalArgumentException("Firefly transaction does not yet have an ID")
        override fun getTimestamp(zoneId: ZoneId): OffsetDateTime {
            return fireflyTransaction.tx.date
        }
        override val plaidTransaction = null
    }

    /**
     * This subtype is used when we've received a transaction from Plaid that already has a corresponding transaction
     * in Firefly. Note that this is NOT related to
     */
    data class MatchedTransaction(
        override val plaidTransaction: Transaction,
        override val fireflyTransaction: FireflyTransactionDto,
    ): PlaidFireflyTransaction {
        override val amount = plaidTransaction.amount
        override val transactionId = plaidTransaction.transactionId
        override val fireflyAccountId get() = getFireflyAccountId(fireflyTransaction)
        override fun getTimestamp(zoneId: ZoneId): OffsetDateTime {
            return getPlaidTransactionDate(plaidTransaction, zoneId)
        }
    }
}
