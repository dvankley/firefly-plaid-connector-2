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
 * Maximum cumulative amount for all payments in the specified interval.
 *
 * @param currency
 * @param `value` The amount of the payment. Must contain at most two digits of precision e.g. `1.23`. Minimum accepted value is `1`.
 */

data class PaymentConsentPeriodicAmountAmount(

    @field:JsonProperty("currency")
    val currency: PaymentAmountCurrency,

    /* The amount of the payment. Must contain at most two digits of precision e.g. `1.23`. Minimum accepted value is `1`. */
    @field:JsonProperty("value")
    val `value`: kotlin.Double

)

