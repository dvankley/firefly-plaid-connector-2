/**
 * The Plaid API
 *
 * The Plaid REST API. Please see https://plaid.com/docs/api for more details.
 *
 * The version of the OpenAPI document: 2020-09-14_1.164.8
 *
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package net.djvk.fireflyPlaidConnector2.api.plaid.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * WalletTransactionGetResponse defines the response schema for `/wallet/transaction/get`
 *
 * @param transactionId A unique ID identifying the transaction
 * @param reference A reference for the transaction
 * @param type The type of the transaction. The supported transaction types that are returned are: `BANK_TRANSFER:` a transaction which credits an e-wallet through an external bank transfer.  `PAYOUT:` a transaction which debits an e-wallet by disbursing funds to a counterparty.  `PIS_PAY_IN:` a payment which credits an e-wallet through Plaid's Payment Initiation Services (PIS) APIs. For more information see the [Payment Initiation endpoints](https://plaid.com/docs/api/products/payment-initiation/).  `REFUND:` a transaction which debits an e-wallet by refunding a previously initated payment made through Plaid's [PIS APIs](https://plaid.com/docs/api/products/payment-initiation/).
 * @param amount
 * @param counterparty
 * @param status
 * @param createdAt Timestamp when the transaction was created, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format.
 * @param requestId A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive.
 */

data class WalletTransactionGetResponse(

    /* A unique ID identifying the transaction */
    @field:JsonProperty("transaction_id")
    val transactionId: kotlin.String,

    /* A reference for the transaction */
    @field:JsonProperty("reference")
    val reference: kotlin.String,

    /* The type of the transaction. The supported transaction types that are returned are: `BANK_TRANSFER:` a transaction which credits an e-wallet through an external bank transfer.  `PAYOUT:` a transaction which debits an e-wallet by disbursing funds to a counterparty.  `PIS_PAY_IN:` a payment which credits an e-wallet through Plaid's Payment Initiation Services (PIS) APIs. For more information see the [Payment Initiation endpoints](https://plaid.com/docs/api/products/payment-initiation/).  `REFUND:` a transaction which debits an e-wallet by refunding a previously initated payment made through Plaid's [PIS APIs](https://plaid.com/docs/api/products/payment-initiation/). */
    @field:JsonProperty("type")
    val type: Type,

    @field:JsonProperty("amount")
    val amount: WalletTransactionAmount,

    @field:JsonProperty("counterparty")
    val counterparty: WalletTransactionCounterparty,

    @field:JsonProperty("status")
    val status: WalletTransactionStatus,

    /* Timestamp when the transaction was created, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format. */
    @field:JsonProperty("created_at")
    val createdAt: java.time.OffsetDateTime,

    /* A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive. */
    @field:JsonProperty("request_id")
    val requestId: kotlin.String

) : kotlin.collections.HashMap<String, kotlin.Any>() {

    /**
     * The type of the transaction. The supported transaction types that are returned are: `BANK_TRANSFER:` a transaction which credits an e-wallet through an external bank transfer.  `PAYOUT:` a transaction which debits an e-wallet by disbursing funds to a counterparty.  `PIS_PAY_IN:` a payment which credits an e-wallet through Plaid's Payment Initiation Services (PIS) APIs. For more information see the [Payment Initiation endpoints](https://plaid.com/docs/api/products/payment-initiation/).  `REFUND:` a transaction which debits an e-wallet by refunding a previously initated payment made through Plaid's [PIS APIs](https://plaid.com/docs/api/products/payment-initiation/).
     *
     * Values: bANKTRANSFER,pAYOUT,pISPAYIN,rEFUND
     */
    enum class Type(val value: kotlin.String) {
        @JsonProperty(value = "BANK_TRANSFER")
        bANKTRANSFER("BANK_TRANSFER"),
        @JsonProperty(value = "PAYOUT")
        pAYOUT("PAYOUT"),
        @JsonProperty(value = "PIS_PAY_IN")
        pISPAYIN("PIS_PAY_IN"),
        @JsonProperty(value = "REFUND")
        rEFUND("REFUND");
    }
}

