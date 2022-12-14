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
 * Transaction information specific to inter-bank transfers. If the transaction was not an inter-bank transfer, all fields will be `null`.  If the `transactions` object was returned by a Transactions endpoint such as `/transactions/get`, the `payment_meta` key will always appear, but no data elements are guaranteed. If the `transactions` object was returned by an Assets endpoint such as `/asset_report/get/` or `/asset_report/pdf/get`, this field will only appear in an Asset Report with Insights.
 *
 * @param referenceNumber The transaction reference number supplied by the financial institution.
 * @param ppdId The ACH PPD ID for the payer.
 * @param payee For transfers, the party that is receiving the transaction.
 * @param byOrderOf The party initiating a wire transfer. Will be `null` if the transaction is not a wire transfer.
 * @param payer For transfers, the party that is paying the transaction.
 * @param paymentMethod The type of transfer, e.g. 'ACH'
 * @param paymentProcessor The name of the payment processor
 * @param reason The payer-supplied description of the transfer.
 */

data class PaymentMeta(

    /* The transaction reference number supplied by the financial institution. */
    @field:JsonProperty("reference_number")
    val referenceNumber: kotlin.String?,

    /* The ACH PPD ID for the payer. */
    @field:JsonProperty("ppd_id")
    val ppdId: kotlin.String?,

    /* For transfers, the party that is receiving the transaction. */
    @field:JsonProperty("payee")
    val payee: kotlin.String?,

    /* The party initiating a wire transfer. Will be `null` if the transaction is not a wire transfer. */
    @field:JsonProperty("by_order_of")
    val byOrderOf: kotlin.String?,

    /* For transfers, the party that is paying the transaction. */
    @field:JsonProperty("payer")
    val payer: kotlin.String?,

    /* The type of transfer, e.g. 'ACH' */
    @field:JsonProperty("payment_method")
    val paymentMethod: kotlin.String?,

    /* The name of the payment processor */
    @field:JsonProperty("payment_processor")
    val paymentProcessor: kotlin.String?,

    /* The payer-supplied description of the transfer. */
    @field:JsonProperty("reason")
    val reason: kotlin.String?

) : kotlin.collections.HashMap<String, kotlin.Any>()

