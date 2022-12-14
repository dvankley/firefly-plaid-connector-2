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
 * Fired when an error is encountered with an Item. The error can be resolved by having the user go through Link’s update mode.
 *
 * @param webhookType `ITEM`
 * @param webhookCode `ERROR`
 * @param itemId The `item_id` of the Item associated with this webhook, warning, or error
 * @param error
 */

data class ItemErrorWebhook(

    /* `ITEM` */
    @field:JsonProperty("webhook_type")
    val webhookType: kotlin.String,

    /* `ERROR` */
    @field:JsonProperty("webhook_code")
    val webhookCode: kotlin.String,

    /* The `item_id` of the Item associated with this webhook, warning, or error */
    @field:JsonProperty("item_id")
    val itemId: kotlin.String,

    @field:JsonProperty("error")
    val error: PlaidError?

) : kotlin.collections.HashMap<String, kotlin.Any>()

