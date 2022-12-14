/**
 * Firefly III API v1.5.6
 *
 * This is the documentation of the Firefly III API. You can find accompanying documentation on the website of Firefly III itself (see below). Please report any bugs or issues. You may use the \"Authorize\" button to try the API below. This file was last generated on 2022-04-04T03:54:41+00:00
 *
 * The version of the OpenAPI document: 1.5.6
 * Contact: james@firefly-iii.org
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

package net.djvk.fireflyPlaidConnector2.api.firefly.models


import com.fasterxml.jackson.annotation.JsonProperty

/**
 *
 *
 * @param currencyId Use either currency_id or currency_code.
 * @param currencyCode Use either currency_id or currency_code.
 * @param amount
 * @param start Start date of the available budget.
 * @param end End date of the available budget.
 */

data class AvailableBudgetUpdate(

    /* Use either currency_id or currency_code. */
    @field:JsonProperty("currency_id")
    val currencyId: kotlin.String? = null,

    /* Use either currency_id or currency_code. */
    @field:JsonProperty("currency_code")
    val currencyCode: kotlin.String? = null,

    @field:JsonProperty("amount")
    val amount: kotlin.String? = null,

    /* Start date of the available budget. */
    @field:JsonProperty("start")
    val start: java.time.LocalDate? = null,

    /* End date of the available budget. */
    @field:JsonProperty("end")
    val end: java.time.LocalDate? = null

)

