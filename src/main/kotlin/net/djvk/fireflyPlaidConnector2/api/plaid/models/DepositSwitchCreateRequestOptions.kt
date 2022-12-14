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
 * Options to configure the `/deposit_switch/create` request. If provided, cannot be `null`.
 *
 * @param webhook The URL registered to receive webhooks when the status of a deposit switch request has changed.
 * @param transactionItemAccessTokens An array of access tokens corresponding to transaction items to use when attempting to match the user to their Payroll Provider. These tokens must be created by the same client id as the one creating the switch, and have access to the transactions product.
 */

data class DepositSwitchCreateRequestOptions(

    /* The URL registered to receive webhooks when the status of a deposit switch request has changed.  */
    @field:JsonProperty("webhook")
    val webhook: kotlin.String? = null,

    /* An array of access tokens corresponding to transaction items to use when attempting to match the user to their Payroll Provider. These tokens must be created by the same client id as the one creating the switch, and have access to the transactions product. */
    @field:JsonProperty("transaction_item_access_tokens")
    val transactionItemAccessTokens: kotlin.collections.List<kotlin.String>? = null

)

