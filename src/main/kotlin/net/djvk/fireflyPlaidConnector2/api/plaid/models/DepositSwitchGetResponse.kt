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
 * DepositSwitchGetResponse defines the response schema for `/deposit_switch/get`
 *
 * @param depositSwitchId The ID of the deposit switch.
 * @param targetAccountId The ID of the bank account the direct deposit was switched to.
 * @param targetItemId The ID of the Item the direct deposit was switched to.
 * @param state  The state, or status, of the deposit switch.  - `initialized` – The deposit switch has been initialized with the user entering the information required to submit the deposit switch request.  - `processing` – The deposit switch request has been submitted and is being processed.  - `completed` – The user's employer has fulfilled the deposit switch request.  - `error` – There was an error processing the deposit switch request.
 * @param accountHasMultipleAllocations When `true`, user’s direct deposit goes to multiple banks. When false, user’s direct deposit only goes to the target account. Always `null` if the deposit switch has not been completed.
 * @param isAllocatedRemainder When `true`, the target account is allocated the remainder of direct deposit after all other allocations have been deducted. When `false`, user’s direct deposit is allocated as a percent or amount. Always `null` if the deposit switch has not been completed.
 * @param percentAllocated The percentage of direct deposit allocated to the target account. Always `null` if the target account is not allocated a percentage or if the deposit switch has not been completed or if `is_allocated_remainder` is true.
 * @param amountAllocated The dollar amount of direct deposit allocated to the target account. Always `null` if the target account is not allocated an amount or if the deposit switch has not been completed.
 * @param dateCreated [ISO 8601](https://wikipedia.org/wiki/ISO_8601) date the deposit switch was created.
 * @param dateCompleted [ISO 8601](https://wikipedia.org/wiki/ISO_8601) date the deposit switch was completed. Always `null` if the deposit switch has not been completed.
 * @param requestId A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive.
 * @param switchMethod The method used to make the deposit switch.  - `instant` – User instantly switched their direct deposit to a new or existing bank account by connecting their payroll or employer account.  - `mail` – User requested that Plaid contact their employer by mail to make the direct deposit switch.  - `pdf` – User generated a PDF or email to be sent to their employer with the information necessary to make the deposit switch.'
 * @param employerName The name of the employer selected by the user. If the user did not select an employer, the value returned is `null`.
 * @param employerId The ID of the employer selected by the user. If the user did not select an employer, the value returned is `null`.
 * @param institutionName The name of the institution selected by the user. If the user did not select an institution, the value returned is `null`.
 * @param institutionId The ID of the institution selected by the user. If the user did not select an institution, the value returned is `null`.
 */

data class DepositSwitchGetResponse(

    /* The ID of the deposit switch. */
    @field:JsonProperty("deposit_switch_id")
    val depositSwitchId: kotlin.String,

    /* The ID of the bank account the direct deposit was switched to. */
    @field:JsonProperty("target_account_id")
    val targetAccountId: kotlin.String?,

    /* The ID of the Item the direct deposit was switched to. */
    @field:JsonProperty("target_item_id")
    val targetItemId: kotlin.String?,

    /*  The state, or status, of the deposit switch.  - `initialized` – The deposit switch has been initialized with the user entering the information required to submit the deposit switch request.  - `processing` – The deposit switch request has been submitted and is being processed.  - `completed` – The user's employer has fulfilled the deposit switch request.  - `error` – There was an error processing the deposit switch request. */
    @field:JsonProperty("state")
    val state: State,

    /* When `true`, user’s direct deposit goes to multiple banks. When false, user’s direct deposit only goes to the target account. Always `null` if the deposit switch has not been completed. */
    @field:JsonProperty("account_has_multiple_allocations")
    val accountHasMultipleAllocations: kotlin.Boolean?,

    /* When `true`, the target account is allocated the remainder of direct deposit after all other allocations have been deducted. When `false`, user’s direct deposit is allocated as a percent or amount. Always `null` if the deposit switch has not been completed. */
    @field:JsonProperty("is_allocated_remainder")
    val isAllocatedRemainder: kotlin.Boolean?,

    /* The percentage of direct deposit allocated to the target account. Always `null` if the target account is not allocated a percentage or if the deposit switch has not been completed or if `is_allocated_remainder` is true. */
    @field:JsonProperty("percent_allocated")
    val percentAllocated: kotlin.Double?,

    /* The dollar amount of direct deposit allocated to the target account. Always `null` if the target account is not allocated an amount or if the deposit switch has not been completed. */
    @field:JsonProperty("amount_allocated")
    val amountAllocated: kotlin.Double?,

    /* [ISO 8601](https://wikipedia.org/wiki/ISO_8601) date the deposit switch was created.  */
    @field:JsonProperty("date_created")
    val dateCreated: java.time.LocalDate,

    /* [ISO 8601](https://wikipedia.org/wiki/ISO_8601) date the deposit switch was completed. Always `null` if the deposit switch has not been completed.  */
    @field:JsonProperty("date_completed")
    val dateCompleted: java.time.LocalDate?,

    /* A unique identifier for the request, which can be used for troubleshooting. This identifier, like all Plaid identifiers, is case sensitive. */
    @field:JsonProperty("request_id")
    val requestId: kotlin.String,

    /* The method used to make the deposit switch.  - `instant` – User instantly switched their direct deposit to a new or existing bank account by connecting their payroll or employer account.  - `mail` – User requested that Plaid contact their employer by mail to make the direct deposit switch.  - `pdf` – User generated a PDF or email to be sent to their employer with the information necessary to make the deposit switch.' */
    @field:JsonProperty("switch_method")
    val switchMethod: SwitchMethod? = null,

    /* The name of the employer selected by the user. If the user did not select an employer, the value returned is `null`. */
    @field:JsonProperty("employer_name")
    val employerName: kotlin.String? = null,

    /* The ID of the employer selected by the user. If the user did not select an employer, the value returned is `null`. */
    @field:JsonProperty("employer_id")
    val employerId: kotlin.String? = null,

    /* The name of the institution selected by the user. If the user did not select an institution, the value returned is `null`. */
    @field:JsonProperty("institution_name")
    val institutionName: kotlin.String? = null,

    /* The ID of the institution selected by the user. If the user did not select an institution, the value returned is `null`. */
    @field:JsonProperty("institution_id")
    val institutionId: kotlin.String? = null

) : kotlin.collections.HashMap<String, kotlin.Any>() {

    /**
     *  The state, or status, of the deposit switch.  - `initialized` – The deposit switch has been initialized with the user entering the information required to submit the deposit switch request.  - `processing` – The deposit switch request has been submitted and is being processed.  - `completed` – The user's employer has fulfilled the deposit switch request.  - `error` – There was an error processing the deposit switch request.
     *
     * Values: initialized,processing,completed,error
     */
    enum class State(val value: kotlin.String) {
        @JsonProperty(value = "initialized")
        initialized("initialized"),
        @JsonProperty(value = "processing")
        processing("processing"),
        @JsonProperty(value = "completed")
        completed("completed"),
        @JsonProperty(value = "error")
        error("error");
    }

    /**
     * The method used to make the deposit switch.  - `instant` – User instantly switched their direct deposit to a new or existing bank account by connecting their payroll or employer account.  - `mail` – User requested that Plaid contact their employer by mail to make the direct deposit switch.  - `pdf` – User generated a PDF or email to be sent to their employer with the information necessary to make the deposit switch.'
     *
     * Values: instant,mail,pdf
     */
    enum class SwitchMethod(val value: kotlin.String) {
        @JsonProperty(value = "instant")
        instant("instant"),
        @JsonProperty(value = "mail")
        mail("mail"),
        @JsonProperty(value = "pdf")
        pdf("pdf");
    }
}

