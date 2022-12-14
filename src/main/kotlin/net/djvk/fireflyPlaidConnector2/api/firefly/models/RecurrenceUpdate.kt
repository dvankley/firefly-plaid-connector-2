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
 *
 *
 * @param title
 * @param description Not to be confused with the description of the actual transaction(s) being created.
 * @param firstDate First time the recurring transaction will fire.
 * @param repeatUntil Date until the recurring transaction can fire. After that date, it's basically inactive. Use either this field or repetitions.
 * @param nrOfRepetitions Max number of created transactions. Use either this field or repeat_until.
 * @param applyRules Whether or not to fire the rules after the creation of a transaction.
 * @param active If the recurrence is even active.
 * @param notes
 * @param repetitions
 * @param transactions
 */

data class RecurrenceUpdate(

    @field:JsonProperty("title")
    val title: kotlin.String? = null,

    /* Not to be confused with the description of the actual transaction(s) being created. */
    @field:JsonProperty("description")
    val description: kotlin.String? = null,

    /* First time the recurring transaction will fire. */
    @field:JsonProperty("first_date")
    val firstDate: java.time.LocalDate? = null,

    /* Date until the recurring transaction can fire. After that date, it's basically inactive. Use either this field or repetitions. */
    @field:JsonProperty("repeat_until")
    val repeatUntil: java.time.LocalDate? = null,

    /* Max number of created transactions. Use either this field or repeat_until. */
    @field:JsonProperty("nr_of_repetitions")
    val nrOfRepetitions: kotlin.Int? = null,

    /* Whether or not to fire the rules after the creation of a transaction. */
    @field:JsonProperty("apply_rules")
    val applyRules: kotlin.Boolean? = null,

    /* If the recurrence is even active. */
    @field:JsonProperty("active")
    val active: kotlin.Boolean? = null,

    @field:JsonProperty("notes")
    val notes: kotlin.String? = null,

    @field:JsonProperty("repetitions")
    val repetitions: kotlin.collections.List<RecurrenceRepetitionUpdate>? = null,

    @field:JsonProperty("transactions")
    val transactions: kotlin.collections.List<RecurrenceTransactionUpdate>? = null

)

