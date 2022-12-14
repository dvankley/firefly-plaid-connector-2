/**
 * Firefly III API v1.5.6
 *
 * This is the documentation of the Firefly III API. You can find accompanying documentation on the website of Firefly III itself (see below). Please report any bugs or issues. You may use the \"Authorize\" button to try the API below. This file was last generated on 2022-04-04T03:54:41+00:00
 *
 * The version of the OpenAPI document: 1.5.6
 * Contact: james@firefly-iii.org
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

package net.djvk.fireflyPlaidConnector2.api.firefly.models


import com.fasterxml.jackson.annotation.JsonProperty

/**
 *
 *
 * @param createdAt
 * @param updatedAt
 * @param webhookMessageId The ID of the webhook message this attempt belongs to.
 * @param statusCode The HTTP status code of the error, if any.
 * @param logs Internal log for this attempt. May contain sensitive user data.
 * @param response Webhook receiver response for this attempt, if any. May contain sensitive user data.
 */

data class WebhookAttempt(

    @field:JsonProperty("created_at")
    val createdAt: java.time.OffsetDateTime? = null,

    @field:JsonProperty("updated_at")
    val updatedAt: java.time.OffsetDateTime? = null,

    /* The ID of the webhook message this attempt belongs to. */
    @field:JsonProperty("webhook_message_id")
    val webhookMessageId: kotlin.String? = null,

    /* The HTTP status code of the error, if any. */
    @field:JsonProperty("status_code")
    val statusCode: kotlin.Int? = null,

    /* Internal log for this attempt. May contain sensitive user data. */
    @field:JsonProperty("logs")
    val logs: kotlin.String? = null,

    /* Webhook receiver response for this attempt, if any. May contain sensitive user data. */
    @field:JsonProperty("response")
    val response: kotlin.String? = null

)

