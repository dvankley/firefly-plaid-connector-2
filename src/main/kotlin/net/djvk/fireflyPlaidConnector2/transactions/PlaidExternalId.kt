package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyExternalId
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidTransactionId

private const val PLAID_EXTERNAL_ID_PREFIX = "plaid-"
private const val PLAID_TRANSFER_REFERENCE_PREFIX = "plaid-transfer-leg-"

internal fun plaidExternalId(transactionId: PlaidTransactionId): FireflyExternalId = "$PLAID_EXTERNAL_ID_PREFIX$transactionId"

internal fun plaidTransactionId(externalId: FireflyExternalId?): PlaidTransactionId? =
    externalId?.takeIf { it.startsWith(PLAID_EXTERNAL_ID_PREFIX) }
        ?.removePrefix(PLAID_EXTERNAL_ID_PREFIX)?.takeIf { it.isNotBlank() }

internal fun plaidTransferReference(transactionId: PlaidTransactionId): String = "$PLAID_TRANSFER_REFERENCE_PREFIX$transactionId"

internal fun isPlaidTransferReference(reference: String?): Boolean =
    reference != null && reference.startsWith(PLAID_TRANSFER_REFERENCE_PREFIX)
