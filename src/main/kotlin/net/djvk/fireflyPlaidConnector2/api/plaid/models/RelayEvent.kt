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
 * The webhook code indicating which endpoint was called. It can be one of `GET_CALLED`, `REFRESH_CALLED` or `AUDIT_COPY_CREATE_CALLED`.
 *
 * Values: gETCALLED,rEFRESHCALLED,aUDITCOPYCREATECALLED
 */

enum class RelayEvent(val value: kotlin.String) {

    @JsonProperty(value = "GET_CALLED")
    gETCALLED("GET_CALLED"),

    @JsonProperty(value = "REFRESH_CALLED")
    rEFRESHCALLED("REFRESH_CALLED"),

    @JsonProperty(value = "AUDIT_COPY_CREATE_CALLED")
    aUDITCOPYCREATECALLED("AUDIT_COPY_CREATE_CALLED");

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
        fun encode(data: kotlin.Any?): kotlin.String? = if (data is RelayEvent) "$data" else null

        /**
         * Returns a valid [RelayEvent] for [data], null otherwise.
         */
        fun decode(data: kotlin.Any?): RelayEvent? = data?.let {
            val normalizedData = "$it".lowercase()
            values().firstOrNull { value ->
                it == value || normalizedData == "$value".lowercase()
            }
        }
    }
}

