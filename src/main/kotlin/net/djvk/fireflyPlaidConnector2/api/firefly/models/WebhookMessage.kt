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
 * @param sent If this message is sent yet.
 * @param errored If this message has errored out.
 * @param webhookId The ID of the webhook this message belongs to.
 * @param uuid Long UUID string for identification of this webhook message.
 * @param string The actual message that is sent or will be sent as JSON string.
 */

data class WebhookMessage(

    @field:JsonProperty("created_at")
    val createdAt: java.time.OffsetDateTime? = null,

    @field:JsonProperty("updated_at")
    val updatedAt: java.time.OffsetDateTime? = null,

    /* If this message is sent yet. */
    @field:JsonProperty("sent")
    val sent: kotlin.Boolean? = null,

    /* If this message has errored out. */
    @field:JsonProperty("errored")
    val errored: kotlin.Boolean? = null,

    /* The ID of the webhook this message belongs to. */
    @field:JsonProperty("webhook_id")
    val webhookId: kotlin.String? = null,

    /* Long UUID string for identification of this webhook message. */
    @field:JsonProperty("uuid")
    val uuid: kotlin.String? = null,

    /* The actual message that is sent or will be sent as JSON string. */
    @field:JsonProperty("string")
    val string: kotlin.String? = null

)

