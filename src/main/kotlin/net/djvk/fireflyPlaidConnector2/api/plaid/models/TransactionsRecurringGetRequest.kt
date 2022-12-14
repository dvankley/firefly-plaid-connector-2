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
 * TransactionsRecurringGetRequest defines the request schema for `/transactions/recurring/get`
 *
 * @param accessToken The access token associated with the Item data is being requested for.
 * @param accountIds A list of `account_ids` to retrieve for the Item  Note: An error will be returned if a provided `account_id` is not associated with the Item.
 * @param clientId Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body.
 * @param secret Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body.
 * @param options
 */

data class TransactionsRecurringGetRequest(

    /* The access token associated with the Item data is being requested for. */
    @field:JsonProperty("access_token")
    val accessToken: kotlin.String,

    /* A list of `account_ids` to retrieve for the Item  Note: An error will be returned if a provided `account_id` is not associated with the Item. */
    @field:JsonProperty("account_ids")
    val accountIds: kotlin.collections.List<kotlin.String>,

    /* Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body. */
    @field:JsonProperty("client_id")
    val clientId: kotlin.String? = null,

    /* Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body. */
    @field:JsonProperty("secret")
    val secret: kotlin.String? = null,

    @field:JsonProperty("options")
    val options: TransactionsRecurringGetRequestOptions? = null

)

