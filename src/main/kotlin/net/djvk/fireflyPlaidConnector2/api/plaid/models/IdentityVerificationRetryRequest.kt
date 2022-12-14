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
 * Request input for retrying an identity verification attempt
 *
 * @param clientUserId An identifier to help you connect this object to your internal systems. For example, your database ID corresponding to this object.
 * @param templateId ID of the associated Identity Verification template.
 * @param strategy
 * @param steps
 * @param clientId Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body.
 * @param secret Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body.
 */

data class IdentityVerificationRetryRequest(

    /* An identifier to help you connect this object to your internal systems. For example, your database ID corresponding to this object. */
    @field:JsonProperty("client_user_id")
    val clientUserId: kotlin.String,

    /* ID of the associated Identity Verification template. */
    @field:JsonProperty("template_id")
    val templateId: kotlin.String,

    @field:JsonProperty("strategy")
    val strategy: Strategy,

    @field:JsonProperty("steps")
    val steps: IdentityVerificationRetryRequestStepsObject? = null,

    /* Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body. */
    @field:JsonProperty("client_id")
    val clientId: kotlin.String? = null,

    /* Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body. */
    @field:JsonProperty("secret")
    val secret: kotlin.String? = null

)

