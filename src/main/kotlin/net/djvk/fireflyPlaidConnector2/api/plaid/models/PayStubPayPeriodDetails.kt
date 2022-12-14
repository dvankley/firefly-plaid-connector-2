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
 * Details about the pay period.
 *
 * @param payAmount The amount of the paycheck.
 * @param distributionBreakdown
 * @param endDate The date on which the pay period ended, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (\"yyyy-mm-dd\").
 * @param grossEarnings Total earnings before tax/deductions.
 * @param isoCurrencyCode The ISO-4217 currency code of the net pay. Always `null` if `unofficial_currency_code` is non-null.
 * @param payDate The date on which the pay stub was issued, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (\"yyyy-mm-dd\").
 * @param payFrequency The frequency at which an individual is paid.
 * @param startDate The date on which the pay period started, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (\"yyyy-mm-dd\").
 * @param unofficialCurrencyCode The unofficial currency code associated with the net pay. Always `null` if `iso_currency_code` is non-`null`. Unofficial currency codes are used for currencies that do not have official ISO currency codes, such as cryptocurrencies and the currencies of certain countries.  See the [currency code schema](https://plaid.com/docs/api/accounts#currency-code-schema) for a full listing of supported `iso_currency_code`s.
 */

data class PayStubPayPeriodDetails(

    /* The amount of the paycheck. */
    @field:JsonProperty("pay_amount")
    val payAmount: kotlin.Double?,

    @field:JsonProperty("distribution_breakdown")
    val distributionBreakdown: kotlin.collections.List<PayStubDistributionBreakdown>,

    /* The date on which the pay period ended, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (\"yyyy-mm-dd\"). */
    @field:JsonProperty("end_date")
    val endDate: java.time.LocalDate?,

    /* Total earnings before tax/deductions. */
    @field:JsonProperty("gross_earnings")
    val grossEarnings: kotlin.Double?,

    /* The ISO-4217 currency code of the net pay. Always `null` if `unofficial_currency_code` is non-null. */
    @field:JsonProperty("iso_currency_code")
    val isoCurrencyCode: kotlin.String?,

    /* The date on which the pay stub was issued, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (\"yyyy-mm-dd\"). */
    @field:JsonProperty("pay_date")
    val payDate: java.time.LocalDate?,

    /* The frequency at which an individual is paid. */
    @field:JsonProperty("pay_frequency")
    val payFrequency: kotlin.String?,

    /* The date on which the pay period started, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) format (\"yyyy-mm-dd\"). */
    @field:JsonProperty("start_date")
    val startDate: java.time.LocalDate?,

    /* The unofficial currency code associated with the net pay. Always `null` if `iso_currency_code` is non-`null`. Unofficial currency codes are used for currencies that do not have official ISO currency codes, such as cryptocurrencies and the currencies of certain countries.  See the [currency code schema](https://plaid.com/docs/api/accounts#currency-code-schema) for a full listing of supported `iso_currency_code`s. */
    @field:JsonProperty("unofficial_currency_code")
    val unofficialCurrencyCode: kotlin.String?

) : kotlin.collections.HashMap<String, kotlin.Any>()

