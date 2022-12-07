package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.api.firefly.models.*
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
        get() = TransactionConverter.getPlaidAmount(this)

    override fun getTimestamp(zoneId: ZoneId): OffsetDateTime {
        return tx.date
    }

    override fun getFireflyAccountId(accountMap: Map<PlaidAccountId, FireflyAccountId>): FireflyAccountId {
        return when (tx.type) {
            TransactionTypeProperty.deposit -> tx.destinationId?.toInt()
                ?: throw IllegalArgumentException("SortableTransaction.getFireflyAccountId can't be called on a deposit " +
                "with a null destination id")
            TransactionTypeProperty.withdrawal -> tx.sourceId?.toInt()
                ?: throw IllegalArgumentException("SortableTransaction.getFireflyAccountId can't be called on a withdrawal " +
                        "with a null source id")
            else -> throw IllegalArgumentException("SortableTransaction.getFireflyAccountId isn't valid to call on " +
                "FireflyTransactionDtos that are not withdrawals or deposits")
        }
    }

    fun toTransactionStore(): TransactionStore {
        return TransactionStore(
            listOf(tx),
            errorIfDuplicateHash = true,
            applyRules = true,
            fireWebhooks = true,
            groupTitle = null,
        )
    }

    fun toTransactionUpdate(): TransactionUpdate {
        return TransactionUpdate(
            transactions = listOf(tx.toTransactionSplitUpdate()),
            applyRules = true,
            fireWebhooks = true,
            groupTitle = tx.description,
        )
    }
}
