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
 * The application requires that accounts be limited to a specific cardinality. `MULTI_SELECT`: indicates that the user should be allowed to pick multiple accounts. `SINGLE_SELECT`: indicates that the user should be allowed to pick only a single account. `ALL`: indicates that the user must share all of their accounts and should not be given the opportunity to de-select
 *
 * Values: sINGLESELECT,mULTISELECT,aLL
 */

enum class AccountSelectionCardinality(val value: kotlin.String) {

    @JsonProperty(value = "SINGLE_SELECT")
    sINGLESELECT("SINGLE_SELECT"),

    @JsonProperty(value = "MULTI_SELECT")
    mULTISELECT("MULTI_SELECT"),

    @JsonProperty(value = "ALL")
    aLL("ALL");

    /**
     * Override toString() to avoid using the enum variable name as the value, and instead use
     * the actual value defined in the API spec file.
     *
     * This solves a problem when the variable name and its value are different, and ensures that
     * the client sends the correct enum values to the server always.
     */
    override fun toString(): String = value

    companion object {
        /**
         * Converts the provided [data] to a [String] on success, null otherwise.
         */
        fun encode(data: kotlin.Any?): kotlin.String? = if (data is AccountSelectionCardinality) "$data" else null

        /**
         * Returns a valid [AccountSelectionCardinality] for [data], null otherwise.
         */
        fun decode(data: kotlin.Any?): AccountSelectionCardinality? = data?.let {
            val normalizedData = "$it".lowercase()
            values().firstOrNull { value ->
                it == value || normalizedData == "$value".lowercase()
            }
        }
    }
}

