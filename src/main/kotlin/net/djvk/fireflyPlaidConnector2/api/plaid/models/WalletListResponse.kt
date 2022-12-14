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
 * WalletListResponse defines the response schema for `/wallet/list`
 *
 * @param wallets An array of e-wallets
 * @param requestId A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive.
 * @param nextCursor Cursor used for fetching e-wallets created before the latest e-wallet provided in this response
 */

data class WalletListResponse(

    /* An array of e-wallets */
    @field:JsonProperty("wallets")
    val wallets: kotlin.collections.List<Wallet>,

    /* A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive. */
    @field:JsonProperty("request_id")
    val requestId: kotlin.String,

    /* Cursor used for fetching e-wallets created before the latest e-wallet provided in this response */
    @field:JsonProperty("next_cursor")
    val nextCursor: kotlin.String? = null

) : kotlin.collections.HashMap<String, kotlin.Any>()

