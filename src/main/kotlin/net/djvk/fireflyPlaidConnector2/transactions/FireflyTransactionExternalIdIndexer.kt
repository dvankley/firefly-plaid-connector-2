package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyExternalId
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidTransactionId

class FireflyTransactionExternalIdIndexer(
    existingFireflyTxs: List<TransactionRead>,
) {
    private val fireflyTxsByExternalId: Map<FireflyExternalId, TransactionRead>

    init {
        val out = mutableMapOf<FireflyExternalId, TransactionRead>()
        for (existingFireflyTx in existingFireflyTxs) {
            for (tx in existingFireflyTx.attributes.transactions) {
                if (tx.externalId == null) continue

                out[tx.externalId] = existingFireflyTx
            }
        }

        fireflyTxsByExternalId = out
    }

    fun findExistingFireflyTx(
        plaidTransactionId: PlaidTransactionId,
    ): TransactionRead? {
        return fireflyTxsByExternalId[getExternalId(plaidTransactionId)]
    }

    companion object {
        fun getExternalId(txId: String): PlaidTransactionId {
            return "plaid-${txId}"
        }
    }
}
