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
 * The address of the employee.
 *
 * @param city The full city name.
 * @param region The region or state Example: `\"NC\"`
 * @param street The full street address Example: `\"564 Main Street, APT 15\"`
 * @param postalCode 5 digit postal code.
 * @param country The country of the address.
 */

data class PaystubOverrideEmployeeAddress(

    /* The full city name. */
    @field:JsonProperty("city")
    val city: kotlin.String? = null,

    /* The region or state Example: `\"NC\"` */
    @field:JsonProperty("region")
    val region: kotlin.String? = null,

    /* The full street address Example: `\"564 Main Street, APT 15\"` */
    @field:JsonProperty("street")
    val street: kotlin.String? = null,

    /* 5 digit postal code. */
    @field:JsonProperty("postal_code")
    val postalCode: kotlin.String? = null,

    /* The country of the address. */
    @field:JsonProperty("country")
    val country: kotlin.String? = null

)

