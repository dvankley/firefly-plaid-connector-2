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
 * By default, Link will provide limited account filtering: it will only display Institutions that are compatible with all products supplied in the `products` parameter of `/link/token/create`, and, if `auth` is specified in the `products` array, will also filter out accounts other than `checking` and `savings` accounts on the Account Select pane. You can further limit the accounts shown in Link by using `account_filters` to specify the account subtypes to be shown in Link. Only the specified subtypes will be shown. This filtering applies to both the Account Select view (if enabled) and the Institution Select view. Institutions that do not support the selected subtypes will be omitted from Link. To indicate that all subtypes should be shown, use the value `\"all\"`. If the `account_filters` filter is used, any account type for which a filter is not specified will be entirely omitted from Link. For a full list of valid types and subtypes, see the [Account schema](https://plaid.com/docs/api/accounts#account-type-schema).  For institutions using OAuth, the filter will not affect the list of accounts shown by the bank in the OAuth window.
 *
 * @param depository
 * @param credit
 * @param loan
 * @param investment
 */

data class LinkTokenAccountFilters(

    @field:JsonProperty("depository")
    val depository: DepositoryFilter? = null,

    @field:JsonProperty("credit")
    val credit: CreditFilter? = null,

    @field:JsonProperty("loan")
    val loan: LoanFilter? = null,

    @field:JsonProperty("investment")
    val investment: InvestmentFilter? = null

) : kotlin.collections.HashMap<String, kotlin.Any>()

