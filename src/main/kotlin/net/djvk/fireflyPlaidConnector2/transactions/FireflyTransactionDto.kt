package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Transaction
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplitUpdate
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionUpdate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * This class is used as an internal DTO because the generated Firefly API model classes are inconsistent
 *  in what data they contain which makes it hard to have consistent internal interfaces.
 */
data class FireflyTransactionDto(
    /**
     * The id of the Firefly transaction. This will only be present if this transaction has been persisted
     *  in Firefly.
     * This will be used to determine if this record should be sent to Firefly as an update or a create.
     * Not to be confused with [TransactionSplit.transactionJournalId].
     */
    val id: FireflyTransactionId?,
    val tx: TransactionSplit,
) : SortableTransaction {
    override val transactionId: String
        get() = id ?: throw RuntimeException("Can't use a Firefly transaction without an id for sorting")

    override val amount: Double
        get() = tx.amount.toDouble()

    override fun getTimestamp(zoneId: ZoneId): OffsetDateTime {
        return tx.date
    }

    fun toTransaction(): Transaction {
        return Transaction(
            listOf(tx),
            tx.date,
        )
    }

    fun toTransactionUpdate(): TransactionUpdate {
        return TransactionUpdate(
            transactions = listOf(tx.toTransactionSplitUpdate()),
        )
    }
}
