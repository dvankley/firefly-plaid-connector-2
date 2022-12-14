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
 * The deposit switch target user
 *
 * @param givenName The given name (first name) of the user.
 * @param familyName The family name (last name) of the user.
 * @param phone The phone number of the user. The endpoint can accept a variety of phone number formats, including E.164.
 * @param email The email address of the user.
 * @param address
 * @param taxPayerId The taxpayer ID of the user, generally their SSN, EIN, or TIN.
 */

data class DepositSwitchTargetUser(

    /* The given name (first name) of the user. */
    @field:JsonProperty("given_name")
    val givenName: kotlin.String,

    /* The family name (last name) of the user. */
    @field:JsonProperty("family_name")
    val familyName: kotlin.String,

    /* The phone number of the user. The endpoint can accept a variety of phone number formats, including E.164. */
    @field:JsonProperty("phone")
    val phone: kotlin.String,

    /* The email address of the user. */
    @field:JsonProperty("email")
    val email: kotlin.String,

    @field:JsonProperty("address")
    val address: DepositSwitchAddressData? = null,

    /* The taxpayer ID of the user, generally their SSN, EIN, or TIN. */
    @field:JsonProperty("tax_payer_id")
    val taxPayerId: kotlin.String? = null

) : kotlin.collections.HashMap<String, kotlin.Any>()

