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
 *  A decision regarding the proposed transfer.  `APPROVED` – The proposed transfer has received the end user's consent and has been approved for processing by Plaid. The `decision_rationale` field is set if Plaid was unable to fetch the account information. You may proceed with the transfer, but further review is recommended (i.e., use Link in update to re-authenticate your user when `decision_rationale.code` is `LOGIN_REQUIRED`). Refer to the `code` field in the `decision_rationale` object for details.  `DECLINED` – Plaid reviewed the proposed transfer and declined processing. Refer to the `code` field in the `decision_rationale` object for details.
 *
 * Values: aPPROVED,dECLINED
 */

enum class TransferIntentAuthorizationDecision(val value: kotlin.String) {

    @JsonProperty(value = "APPROVED")
    aPPROVED("APPROVED"),

    @JsonProperty(value = "DECLINED")
    dECLINED("DECLINED");

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
        fun encode(data: kotlin.Any?): kotlin.String? =
            if (data is TransferIntentAuthorizationDecision) "$data" else null

        /**
         * Returns a valid [TransferIntentAuthorizationDecision] for [data], null otherwise.
         */
        fun decode(data: kotlin.Any?): TransferIntentAuthorizationDecision? = data?.let {
            val normalizedData = "$it".lowercase()
            values().firstOrNull { value ->
                it == value || normalizedData == "$value".lowercase()
            }
        }
    }
}

