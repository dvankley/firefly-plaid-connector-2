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
 * Analyzed location information for the associated hit
 *
 * @param analysis
 * @param `data`
 */

data class GenericScreeningHitLocationItems(

    @field:JsonProperty("analysis")
    val analysis: MatchSummary? = null,

    @field:JsonProperty("data")
    val `data`: WatchlistScreeningHitLocations? = null

)

