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
 * An object containing account level data.
 *
 * @param accountId ID of the payroll provider account.
 * @param rateOfPay
 * @param payFrequency The frequency at which an individual is paid.
 */

data class PayrollIncomeAccountData(

    /* ID of the payroll provider account. */
    @field:JsonProperty("account_id")
    val accountId: kotlin.String?,

    @field:JsonProperty("rate_of_pay")
    val rateOfPay: PayrollIncomeRateOfPay,

    /* The frequency at which an individual is paid. */
    @field:JsonProperty("pay_frequency")
    val payFrequency: kotlin.String?

) : kotlin.collections.HashMap<String, kotlin.Any>()

