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
 * The details and metadata for an end user's Item.
 *
 * @param bankIncomeAccounts The Item's accounts that have Bank Income data.
 * @param bankIncomeSources The income sources for this Item. Each entry in the array is a single income source.
 * @param lastUpdatedTime The time when this Item's data was last retrieved from the financial institution.
 * @param institutionId The unique identifier of the institution associated with the Item.
 * @param institutionName The name of the institution associated with the Item.
 * @param itemId The unique identifier for the Item.
 */

data class CreditBankIncomeItem(

    /* The Item's accounts that have Bank Income data. */
    @field:JsonProperty("bank_income_accounts")
    val bankIncomeAccounts: kotlin.collections.List<CreditBankIncomeAccount>? = null,

    /* The income sources for this Item. Each entry in the array is a single income source. */
    @field:JsonProperty("bank_income_sources")
    val bankIncomeSources: kotlin.collections.List<CreditBankIncomeSource>? = null,

    /* The time when this Item's data was last retrieved from the financial institution. */
    @field:JsonProperty("last_updated_time")
    val lastUpdatedTime: java.time.OffsetDateTime? = null,

    /* The unique identifier of the institution associated with the Item. */
    @field:JsonProperty("institution_id")
    val institutionId: kotlin.String? = null,

    /* The name of the institution associated with the Item. */
    @field:JsonProperty("institution_name")
    val institutionName: kotlin.String? = null,

    /* The unique identifier for the Item. */
    @field:JsonProperty("item_id")
    val itemId: kotlin.String? = null

)

