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
 * TransactionsRecurringGetResponse defines the response schema for `/transactions/recurring/get`
 *
 * @param inflowStreams An array of depository transaction streams.
 * @param outflowStreams An array of expense transaction streams.
 * @param updatedDatetime Timestamp in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (`YYYY-MM-DDTHH:mm:ssZ`) indicating the last time transaction streams for the given account were updated on
 * @param requestId A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive.
 */

data class TransactionsRecurringGetResponse(

    /* An array of depository transaction streams. */
    @field:JsonProperty("inflow_streams")
    val inflowStreams: kotlin.collections.List<TransactionStream>,

    /* An array of expense transaction streams. */
    @field:JsonProperty("outflow_streams")
    val outflowStreams: kotlin.collections.List<TransactionStream>,

    /* Timestamp in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (`YYYY-MM-DDTHH:mm:ssZ`) indicating the last time transaction streams for the given account were updated on */
    @field:JsonProperty("updated_datetime")
    val updatedDatetime: java.time.OffsetDateTime,

    /* A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive. */
    @field:JsonProperty("request_id")
    val requestId: kotlin.String

) : kotlin.collections.HashMap<String, kotlin.Any>()

