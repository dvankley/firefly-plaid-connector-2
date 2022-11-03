package net.djvk.fireflyPlaidConnector2.transactions

import java.time.OffsetDateTime
import java.time.ZoneId

interface SortableTransaction {
    val transactionId: String

    /**
     * This should be the Plaid amount, as returned by [TransactionConverter.getPlaidAmount] for Firefly
     *  transactions.
     */
    val amount: Double
    fun getTimestamp(zoneId: ZoneId): OffsetDateTime
}