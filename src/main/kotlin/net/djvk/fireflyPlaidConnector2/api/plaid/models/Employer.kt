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
 * Data about the employer.
 *
 * @param employerId Plaid's unique identifier for the employer.
 * @param name The name of the employer
 * @param address
 * @param confidenceScore A number from 0 to 1 indicating Plaid's level of confidence in the pairing between the employer and the institution (not yet implemented).
 */

data class Employer(

    /* Plaid's unique identifier for the employer. */
    @field:JsonProperty("employer_id")
    val employerId: kotlin.String,

    /* The name of the employer */
    @field:JsonProperty("name")
    val name: kotlin.String,

    @field:JsonProperty("address")
    val address: AddressDataNullable?,

    /* A number from 0 to 1 indicating Plaid's level of confidence in the pairing between the employer and the institution (not yet implemented). */
    @field:JsonProperty("confidence_score")
    val confidenceScore: kotlin.Double

) : kotlin.collections.HashMap<String, kotlin.Any>()

