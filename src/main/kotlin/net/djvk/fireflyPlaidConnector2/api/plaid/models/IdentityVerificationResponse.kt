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
 * A identity verification attempt represents a customer's attempt to verify their identity, reflecting the required steps for completing the session, the results for each step, and information collected in the process.
 *
 * @param id ID of the associated Identity Verification attempt.
 * @param clientUserId An identifier to help you connect this object to your internal systems. For example, your database ID corresponding to this object.
 * @param createdAt An ISO8601 formatted timestamp.
 * @param completedAt An ISO8601 formatted timestamp.
 * @param previousAttemptId The ID for the Identity Verification preceding this session. This field will only be filled if the current Identity Verification is a retry of a previous attempt.
 * @param shareableUrl A shareable URL that can be sent directly to the user to complete verification
 * @param template
 * @param user
 * @param status
 * @param steps
 * @param documentaryVerification
 * @param kycCheck
 * @param watchlistScreeningId
 * @param requestId A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive.
 */

data class IdentityVerificationResponse(

    /* ID of the associated Identity Verification attempt. */
    @field:JsonProperty("id")
    val id: kotlin.String,

    /* An identifier to help you connect this object to your internal systems. For example, your database ID corresponding to this object. */
    @field:JsonProperty("client_user_id")
    val clientUserId: kotlin.String,

    /* An ISO8601 formatted timestamp. */
    @field:JsonProperty("created_at")
    val createdAt: java.time.OffsetDateTime,

    /* An ISO8601 formatted timestamp. */
    @field:JsonProperty("completed_at")
    val completedAt: java.time.OffsetDateTime?,

    /* The ID for the Identity Verification preceding this session. This field will only be filled if the current Identity Verification is a retry of a previous attempt. */
    @field:JsonProperty("previous_attempt_id")
    val previousAttemptId: kotlin.String?,

    /* A shareable URL that can be sent directly to the user to complete verification */
    @field:JsonProperty("shareable_url")
    val shareableUrl: kotlin.String?,

    @field:JsonProperty("template")
    val template: IdentityVerificationTemplateReference,

    @field:JsonProperty("user")
    val user: IdentityVerificationUserData,

    @field:JsonProperty("status")
    val status: IdentityVerificationStatus,

    @field:JsonProperty("steps")
    val steps: IdentityVerificationStepSummary,

    @field:JsonProperty("documentary_verification")
    val documentaryVerification: DocumentaryVerification?,

    @field:JsonProperty("kyc_check")
    val kycCheck: KYCCheckDetails?,

    @field:JsonProperty("watchlist_screening_id")
    val watchlistScreeningId: kotlin.String?,

    /* A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive. */
    @field:JsonProperty("request_id")
    val requestId: kotlin.String

) : kotlin.collections.HashMap<String, kotlin.Any>()

