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

    /**
     * Returns the Firefly account id that this transaction is "on," which is the Firefly source id for Firefly withdrawals,
     *  Firefly destination id for Firefly deposits, and the account id for Plaid transactions.
     */
    fun getFireflyAccountId(accountMap: Map<PlaidAccountId, FireflyAccountId>): FireflyAccountId
}