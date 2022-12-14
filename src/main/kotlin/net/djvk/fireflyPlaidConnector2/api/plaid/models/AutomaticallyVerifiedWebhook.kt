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
 * Fired when an Item is verified via automated micro-deposits. We recommend communicating to your users when this event is received to notify them that their account is verified and ready for use.
 *
 * @param webhookType `AUTH`
 * @param webhookCode `AUTOMATICALLY_VERIFIED`
 * @param accountId The `account_id` of the account associated with the webhook
 * @param itemId The `item_id` of the Item associated with this webhook, warning, or error
 */

data class AutomaticallyVerifiedWebhook(

    /* `AUTH` */
    @field:JsonProperty("webhook_type")
    val webhookType: kotlin.String,

    /* `AUTOMATICALLY_VERIFIED` */
    @field:JsonProperty("webhook_code")
    val webhookCode: kotlin.String,

    /* The `account_id` of the account associated with the webhook */
    @field:JsonProperty("account_id")
    val accountId: kotlin.String,

    /* The `item_id` of the Item associated with this webhook, warning, or error */
    @field:JsonProperty("item_id")
    val itemId: kotlin.String

) : kotlin.collections.HashMap<String, kotlin.Any>()

