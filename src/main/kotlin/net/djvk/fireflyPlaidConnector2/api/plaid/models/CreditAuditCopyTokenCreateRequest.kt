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
 * CreditAuditCopyTokenCreateRequest defines the request schema for `/credit/audit_copy_token/create`
 *
 * @param reportTokens List of report tokens; can include both Asset Report tokens and Income Report tokens.
 * @param auditorId The `auditor_id` of the third party with whom you would like to share the Asset Report and/or Income Report.
 * @param clientId Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body.
 * @param secret Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body.
 */

data class CreditAuditCopyTokenCreateRequest(

    /* List of report tokens; can include both Asset Report tokens and Income Report tokens. */
    @field:JsonProperty("report_tokens")
    val reportTokens: kotlin.collections.List<ReportToken>,

    /* The `auditor_id` of the third party with whom you would like to share the Asset Report and/or Income Report. */
    @field:JsonProperty("auditor_id")
    val auditorId: kotlin.String,

    /* Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body. */
    @field:JsonProperty("client_id")
    val clientId: kotlin.String? = null,

    /* Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body. */
    @field:JsonProperty("secret")
    val secret: kotlin.String? = null

)

