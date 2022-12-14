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
 * Search terms for editing an entity watchlist screening
 *
 * @param entityWatchlistProgramId ID of the associated entity program.
 * @param clientId Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body.
 * @param secret Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body.
 * @param legalName
 * @param documentNumber
 * @param emailAddress
 * @param country
 * @param phoneNumber
 * @param url
 */

data class UpdateEntityScreeningRequestSearchTerms(

    /* ID of the associated entity program. */
    @field:JsonProperty("entity_watchlist_program_id")
    val entityWatchlistProgramId: kotlin.String,

    /* Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body. */
    @field:JsonProperty("client_id")
    val clientId: kotlin.String,

    /* Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body. */
    @field:JsonProperty("secret")
    val secret: kotlin.String,

    @field:JsonProperty("legal_name")
    val legalName: kotlin.String? = null,

    @field:JsonProperty("document_number")
    val documentNumber: kotlin.String? = null,

    @field:JsonProperty("email_address")
    val emailAddress: kotlin.String? = null,

    @field:JsonProperty("country")
    val country: kotlin.String? = null,

    @field:JsonProperty("phone_number")
    val phoneNumber: kotlin.String? = null,

    @field:JsonProperty("url")
    val url: java.net.URI? = null

)

