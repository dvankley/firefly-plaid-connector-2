package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyExternalId
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidTransactionId

internal fun plaidExternalId(transactionId: PlaidTransactionId): FireflyExternalId = "plaid-$transactionId"
