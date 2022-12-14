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
 * Information about the student's eligibility in the Public Service Loan Forgiveness program. This is only returned if the institution is Fedloan (`ins_116527`).
 *
 * @param estimatedEligibilityDate The estimated date borrower will have completed 120 qualifying monthly payments. Returned in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (YYYY-MM-DD).
 * @param paymentsMade The number of qualifying payments that have been made.
 * @param paymentsRemaining The number of qualifying payments remaining.
 */

data class PSLFStatus(

    /* The estimated date borrower will have completed 120 qualifying monthly payments. Returned in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (YYYY-MM-DD). */
    @field:JsonProperty("estimated_eligibility_date")
    val estimatedEligibilityDate: java.time.LocalDate?,

    /* The number of qualifying payments that have been made. */
    @field:JsonProperty("payments_made")
    val paymentsMade: java.math.BigDecimal?,

    /* The number of qualifying payments remaining. */
    @field:JsonProperty("payments_remaining")
    val paymentsRemaining: java.math.BigDecimal?

) : kotlin.collections.HashMap<String, kotlin.Any>()

