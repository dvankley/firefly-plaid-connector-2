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
 * Identifying information for transferring money to or from an international bank account via wire transfer.
 *
 * @param accountId The Plaid account ID associated with the account numbers
 * @param iban The International Bank Account Number (IBAN) for the account
 * @param bic The Bank Identifier Code (BIC) for the account
 */

data class NumbersInternational(

    /* The Plaid account ID associated with the account numbers */
    @field:JsonProperty("account_id")
    val accountId: kotlin.String,

    /* The International Bank Account Number (IBAN) for the account */
    @field:JsonProperty("iban")
    val iban: kotlin.String,

    /* The Bank Identifier Code (BIC) for the account */
    @field:JsonProperty("bic")
    val bic: kotlin.String

) : kotlin.collections.HashMap<String, kotlin.Any>()

