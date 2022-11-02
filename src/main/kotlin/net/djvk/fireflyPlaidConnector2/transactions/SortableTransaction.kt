package net.djvk.fireflyPlaidConnector2.transactions

import java.time.OffsetDateTime
import java.time.ZoneId

interface SortableTransaction {
    val transactionId: String
    val amount: Double
    fun getTimestamp(zoneId: ZoneId): OffsetDateTime
}