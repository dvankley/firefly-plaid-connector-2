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
 * An object containing a BACS account number and sort code. If an IBAN is not provided or if you need to accept domestic GBP-denominated payments, BACS data is required.
 *
 * @param account The account number of the account. Maximum of 10 characters.
 * @param sortCode The 6-character sort code of the account.
 */

data class RecipientBACS(

    /* The account number of the account. Maximum of 10 characters. */
    @field:JsonProperty("account")
    val account: kotlin.String? = null,

    /* The 6-character sort code of the account. */
    @field:JsonProperty("sort_code")
    val sortCode: kotlin.String? = null

) : kotlin.collections.HashMap<String, kotlin.Any>()

