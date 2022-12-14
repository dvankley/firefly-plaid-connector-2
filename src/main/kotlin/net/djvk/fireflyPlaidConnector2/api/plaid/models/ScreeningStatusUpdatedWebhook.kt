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
 * Fired when an individual screening status has changed, which can occur manually via the dashboard or during ongoing monitoring.
 *
 * @param webhookType `SCREENING`
 * @param webhookCode `STATUS_UPDATED`
 * @param screeningId The ID of the associated screening.
 */

data class ScreeningStatusUpdatedWebhook(

    /* `SCREENING` */
    @field:JsonProperty("webhook_type")
    val webhookType: kotlin.String,

    /* `STATUS_UPDATED` */
    @field:JsonProperty("webhook_code")
    val webhookCode: kotlin.String,

    /* The ID of the associated screening. */
    @field:JsonProperty("screening_id")
    val screeningId: kotlin.Any?

) : kotlin.collections.HashMap<String, kotlin.Any>()

