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
 * Configuration parameters for EU flows
 *
 * @param headless If `true`, open Link without an initial UI. Defaults to `false`.
 */

data class LinkTokenEUConfig(

    /* If `true`, open Link without an initial UI. Defaults to `false`. */
    @field:JsonProperty("headless")
    val headless: kotlin.Boolean? = null

)

