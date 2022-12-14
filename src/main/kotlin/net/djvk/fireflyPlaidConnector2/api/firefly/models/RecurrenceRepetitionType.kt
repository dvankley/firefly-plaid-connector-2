/**
 * Firefly III API v1.5.6
 *
 * This is the documentation of the Firefly III API. You can find accompanying documentation on the website of Firefly III itself (see below). Please report any bugs or issues. You may use the \"Authorize\" button to try the API below. This file was last generated on 2022-04-04T03:54:41+00:00
 *
 * The version of the OpenAPI document: 1.5.6
 * Contact: james@firefly-iii.org
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

package net.djvk.fireflyPlaidConnector2.api.firefly.models


import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The type of the repetition. ndom means: the n-th weekday of the month, where you can also specify which day of the week.
 *
 * Values: daily,weekly,ndom,monthly,yearly
 */

enum class RecurrenceRepetitionType(val value: kotlin.String) {

    @JsonProperty(value = "daily")
    daily("daily"),

    @JsonProperty(value = "weekly")
    weekly("weekly"),

    @JsonProperty(value = "ndom")
    ndom("ndom"),

    @JsonProperty(value = "monthly")
    monthly("monthly"),

    @JsonProperty(value = "yearly")
    yearly("yearly");

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
        fun encode(data: kotlin.Any?): kotlin.String? = if (data is RecurrenceRepetitionType) "$data" else null

        /**
         * Returns a valid [RecurrenceRepetitionType] for [data], null otherwise.
         */
        fun decode(data: kotlin.Any?): RecurrenceRepetitionType? = data?.let {
            val normalizedData = "$it".lowercase()
            values().firstOrNull { value ->
                it == value || normalizedData == "$value".lowercase()
            }
        }
    }
}

