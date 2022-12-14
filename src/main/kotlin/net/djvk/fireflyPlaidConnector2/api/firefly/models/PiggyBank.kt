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
 * @param accountId The ID of the asset account this piggy bank is connected to.
 * @param name
 * @param targetAmount
 * @param createdAt
 * @param updatedAt
 * @param accountName The name of the asset account this piggy bank is connected to.
 * @param currencyId
 * @param currencyCode
 * @param currencySymbol
 * @param currencyDecimalPlaces Number of decimals supported by the currency
 * @param percentage
 * @param currentAmount
 * @param leftToSave
 * @param savePerMonth
 * @param startDate The date you started with this piggy bank.
 * @param targetDate The date you intend to finish saving money.
 * @param order
 * @param active
 * @param notes
 * @param objectGroupId The group ID of the group this object is part of. NULL if no group.
 * @param objectGroupOrder The order of the group. At least 1, for the highest sorting.
 * @param objectGroupTitle The name of the group. NULL if no group.
 */

data class PiggyBank(

    /* The ID of the asset account this piggy bank is connected to. */
    @field:JsonProperty("account_id")
    val accountId: kotlin.String,

    @field:JsonProperty("name")
    val name: kotlin.String,

    @field:JsonProperty("target_amount")
    val targetAmount: kotlin.String?,

    @field:JsonProperty("created_at")
    val createdAt: java.time.OffsetDateTime? = null,

    @field:JsonProperty("updated_at")
    val updatedAt: java.time.OffsetDateTime? = null,

    /* The name of the asset account this piggy bank is connected to. */
    @field:JsonProperty("account_name")
    val accountName: kotlin.String? = null,

    @field:JsonProperty("currency_id")
    val currencyId: kotlin.String? = null,

    @field:JsonProperty("currency_code")
    val currencyCode: kotlin.String? = null,

    @field:JsonProperty("currency_symbol")
    val currencySymbol: kotlin.String? = null,

    /* Number of decimals supported by the currency */
    @field:JsonProperty("currency_decimal_places")
    val currencyDecimalPlaces: kotlin.Int? = null,

    @field:JsonProperty("percentage")
    val percentage: kotlin.Float? = null,

    @field:JsonProperty("current_amount")
    val currentAmount: kotlin.String? = null,

    @field:JsonProperty("left_to_save")
    val leftToSave: kotlin.String? = null,

    @field:JsonProperty("save_per_month")
    val savePerMonth: kotlin.String? = null,

    /* The date you started with this piggy bank. */
    @field:JsonProperty("start_date")
    val startDate: java.time.OffsetDateTime? = null,

    /* The date you intend to finish saving money. */
    @field:JsonProperty("target_date")
    val targetDate: java.time.OffsetDateTime? = null,

    @field:JsonProperty("order")
    val order: kotlin.Int? = null,

    @field:JsonProperty("active")
    val active: kotlin.Boolean? = null,

    @field:JsonProperty("notes")
    val notes: kotlin.String? = null,

    /* The group ID of the group this object is part of. NULL if no group. */
    @field:JsonProperty("object_group_id")
    val objectGroupId: kotlin.String? = null,

    /* The order of the group. At least 1, for the highest sorting. */
    @field:JsonProperty("object_group_order")
    val objectGroupOrder: kotlin.Int? = null,

    /* The name of the group. NULL if no group. */
    @field:JsonProperty("object_group_title")
    val objectGroupTitle: kotlin.String? = null

)

