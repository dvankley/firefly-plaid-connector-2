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
 * Limitations that will be applied to payments initiated using the payment consent.
 *
 * @param maxPaymentAmount
 * @param periodicAmounts A list of amount limitations per period of time.
 * @param validDateTime
 */

data class PaymentInitiationConsentConstraints(

    @field:JsonProperty("max_payment_amount")
    val maxPaymentAmount: PaymentConsentMaxPaymentAmount,

    /* A list of amount limitations per period of time. */
    @field:JsonProperty("periodic_amounts")
    val periodicAmounts: kotlin.collections.List<PaymentConsentPeriodicAmount>,

    @field:JsonProperty("valid_date_time")
    val validDateTime: PaymentConsentValidDateTime? = null

) : kotlin.collections.HashMap<String, kotlin.Any>()

