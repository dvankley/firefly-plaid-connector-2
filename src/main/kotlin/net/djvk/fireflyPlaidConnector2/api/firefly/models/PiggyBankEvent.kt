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
 * @param createdAt
 * @param updatedAt
 * @param currencyId
 * @param currencyCode
 * @param currencySymbol
 * @param currencyDecimalPlaces
 * @param amount
 * @param transactionJournalId The journal associated with the event.
 * @param transactionGroupId The transaction group associated with the event.
 */

data class PiggyBankEvent(

    @field:JsonProperty("created_at")
    val createdAt: java.time.OffsetDateTime? = null,

    @field:JsonProperty("updated_at")
    val updatedAt: java.time.OffsetDateTime? = null,

    @field:JsonProperty("currency_id")
    val currencyId: kotlin.String? = null,

    @field:JsonProperty("currency_code")
    val currencyCode: kotlin.String? = null,

    @field:JsonProperty("currency_symbol")
    val currencySymbol: kotlin.String? = null,

    @field:JsonProperty("currency_decimal_places")
    val currencyDecimalPlaces: kotlin.Int? = null,

    @field:JsonProperty("amount")
    val amount: kotlin.String? = null,

    /* The journal associated with the event. */
    @field:JsonProperty("transaction_journal_id")
    val transactionJournalId: kotlin.String? = null,

    /* The transaction group associated with the event. */
    @field:JsonProperty("transaction_group_id")
    val transactionGroupId: kotlin.String? = null

)

