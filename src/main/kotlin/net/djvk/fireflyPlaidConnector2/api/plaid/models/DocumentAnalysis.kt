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
 * High level descriptions of how the associated document was processed. If a document fails verification, the details in the `analysis` object should help clarify why the document was rejected.
 *
 * @param authenticity
 * @param imageQuality
 * @param extractedData
 */

data class DocumentAnalysis(

    @field:JsonProperty("authenticity")
    val authenticity: DocumentAuthenticityMatchCode,

    @field:JsonProperty("image_quality")
    val imageQuality: ImageQuality,

    @field:JsonProperty("extracted_data")
    val extractedData: PhysicalDocumentExtractedDataAnalysis?

)

