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
 * Parent container for name that allows for choice group between parsed and unparsed containers.Parent container for name that allows for choice group between parsed and unparsed containers.
 *
 * @param firstName The first name of the individual represented by the parent object.
 * @param middleName The middle name of the individual represented by the parent object.
 * @param lastName The last name of the individual represented by the parent object.
 */

data class IndividualName(

    /* The first name of the individual represented by the parent object. */
    @field:JsonProperty("FirstName")
    val firstName: kotlin.String,

    /* The middle name of the individual represented by the parent object. */
    @field:JsonProperty("MiddleName")
    val middleName: kotlin.String?,

    /* The last name of the individual represented by the parent object. */
    @field:JsonProperty("LastName")
    val lastName: kotlin.String

) : kotlin.collections.HashMap<String, kotlin.Any>()

