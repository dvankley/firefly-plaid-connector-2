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
 * Account numbers using the International Bank Account Number and BIC/SWIFT code format.
 *
 * @param iban International Bank Account Number (IBAN).
 * @param bic The Business Identifier Code, also known as SWIFT code, for this bank account.
 */

data class NumbersInternationalIBAN(

    /* International Bank Account Number (IBAN). */
    @field:JsonProperty("iban")
    val iban: kotlin.String,

    /* The Business Identifier Code, also known as SWIFT code, for this bank account. */
    @field:JsonProperty("bic")
    val bic: kotlin.String

) : kotlin.collections.HashMap<String, kotlin.Any>()

