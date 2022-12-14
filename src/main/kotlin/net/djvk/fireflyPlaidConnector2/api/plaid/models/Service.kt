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
 * A collection of details related to a fulfillment service or product in terms of request, process and result.
 *
 * @param VERIFICATION_OF_ASSET
 * @param STATUSES
 */

data class Service(

    @field:JsonProperty("VERIFICATION_OF_ASSET")
    val VERIFICATION_OF_ASSET: VerificationOfAsset,

    @field:JsonProperty("STATUSES")
    val STATUSES: Statuses

) : kotlin.collections.HashMap<String, kotlin.Any>()

