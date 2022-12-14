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
 * The webhook types that can be fired by this test endpoint.
 *
 * Values: aUTH,hOLDINGS,iNVESTMENTSTRANSACTIONS,iTEM,lIABILITIES,tRANSACTIONS
 */

enum class WebhookType(val value: kotlin.String) {

    @JsonProperty(value = "AUTH")
    aUTH("AUTH"),

    @JsonProperty(value = "HOLDINGS")
    hOLDINGS("HOLDINGS"),

    @JsonProperty(value = "INVESTMENTS_TRANSACTIONS")
    iNVESTMENTSTRANSACTIONS("INVESTMENTS_TRANSACTIONS"),

    @JsonProperty(value = "ITEM")
    iTEM("ITEM"),

    @JsonProperty(value = "LIABILITIES")
    lIABILITIES("LIABILITIES"),

    @JsonProperty(value = "TRANSACTIONS")
    tRANSACTIONS("TRANSACTIONS");

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
        fun encode(data: kotlin.Any?): kotlin.String? = if (data is WebhookType) "$data" else null

        /**
         * Returns a valid [WebhookType] for [data], null otherwise.
         */
        fun decode(data: kotlin.Any?): WebhookType? = data?.let {
            val normalizedData = "$it".lowercase()
            values().firstOrNull { value ->
                it == value || normalizedData == "$value".lowercase()
            }
        }
    }
}

