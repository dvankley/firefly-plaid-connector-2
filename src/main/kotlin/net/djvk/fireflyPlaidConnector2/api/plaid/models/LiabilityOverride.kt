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
 * Used to configure Sandbox test data for the Liabilities product
 *
 * @param type The type of the liability object, either `credit` or `student`. Mortgages are not currently supported in the custom Sandbox.
 * @param purchaseApr The purchase APR percentage value. For simplicity, this is the only interest rate used to calculate interest charges. Can only be set if `type` is `credit`.
 * @param cashApr The cash APR percentage value. Can only be set if `type` is `credit`.
 * @param balanceTransferApr The balance transfer APR percentage value. Can only be set if `type` is `credit`. Can only be set if `type` is `credit`.
 * @param specialApr The special APR percentage value. Can only be set if `type` is `credit`.
 * @param lastPaymentAmount Override the `last_payment_amount` field. Can only be set if `type` is `credit`.
 * @param minimumPaymentAmount Override the `minimum_payment_amount` field. Can only be set if `type` is `credit` or `student`.
 * @param isOverdue Override the `is_overdue` field
 * @param originationDate The date on which the loan was initially lent, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) (YYYY-MM-DD) format. Can only be set if `type` is `student`.
 * @param principal The original loan principal. Can only be set if `type` is `student`.
 * @param nominalApr The interest rate on the loan as a percentage. Can only be set if `type` is `student`.
 * @param interestCapitalizationGracePeriodMonths If set, interest capitalization begins at the given number of months after loan origination. By default interest is never capitalized. Can only be set if `type` is `student`.
 * @param repaymentModel
 * @param expectedPayoffDate Override the `expected_payoff_date` field. Can only be set if `type` is `student`.
 * @param guarantor Override the `guarantor` field. Can only be set if `type` is `student`.
 * @param isFederal Override the `is_federal` field. Can only be set if `type` is `student`.
 * @param loanName Override the `loan_name` field. Can only be set if `type` is `student`.
 * @param loanStatus
 * @param paymentReferenceNumber Override the `payment_reference_number` field. Can only be set if `type` is `student`.
 * @param pslfStatus
 * @param repaymentPlanDescription Override the `repayment_plan.description` field. Can only be set if `type` is `student`.
 * @param repaymentPlanType Override the `repayment_plan.type` field. Can only be set if `type` is `student`. Possible values are: `\"extended graduated\"`, `\"extended standard\"`, `\"graduated\"`, `\"income-contingent repayment\"`, `\"income-based repayment\"`, `\"interest only\"`, `\"other\"`, `\"pay as you earn\"`, `\"revised pay as you earn\"`, or `\"standard\"`.
 * @param sequenceNumber Override the `sequence_number` field. Can only be set if `type` is `student`.
 * @param servicerAddress
 */

data class LiabilityOverride(

    /* The type of the liability object, either `credit` or `student`. Mortgages are not currently supported in the custom Sandbox. */
    @field:JsonProperty("type")
    val type: kotlin.String,

    /* The purchase APR percentage value. For simplicity, this is the only interest rate used to calculate interest charges. Can only be set if `type` is `credit`. */
    @field:JsonProperty("purchase_apr")
    val purchaseApr: kotlin.Double,

    /* The cash APR percentage value. Can only be set if `type` is `credit`. */
    @field:JsonProperty("cash_apr")
    val cashApr: kotlin.Double,

    /* The balance transfer APR percentage value. Can only be set if `type` is `credit`. Can only be set if `type` is `credit`. */
    @field:JsonProperty("balance_transfer_apr")
    val balanceTransferApr: kotlin.Double,

    /* The special APR percentage value. Can only be set if `type` is `credit`. */
    @field:JsonProperty("special_apr")
    val specialApr: kotlin.Double,

    /* Override the `last_payment_amount` field. Can only be set if `type` is `credit`. */
    @field:JsonProperty("last_payment_amount")
    val lastPaymentAmount: kotlin.Double,

    /* Override the `minimum_payment_amount` field. Can only be set if `type` is `credit` or `student`. */
    @field:JsonProperty("minimum_payment_amount")
    val minimumPaymentAmount: kotlin.Double,

    /* Override the `is_overdue` field */
    @field:JsonProperty("is_overdue")
    val isOverdue: kotlin.Boolean,

    /* The date on which the loan was initially lent, in [ISO 8601](https://wikipedia.org/wiki/ISO_8601) (YYYY-MM-DD) format. Can only be set if `type` is `student`. */
    @field:JsonProperty("origination_date")
    val originationDate: java.time.LocalDate,

    /* The original loan principal. Can only be set if `type` is `student`. */
    @field:JsonProperty("principal")
    val principal: kotlin.Double,

    /* The interest rate on the loan as a percentage. Can only be set if `type` is `student`. */
    @field:JsonProperty("nominal_apr")
    val nominalApr: kotlin.Double,

    /* If set, interest capitalization begins at the given number of months after loan origination. By default interest is never capitalized. Can only be set if `type` is `student`. */
    @field:JsonProperty("interest_capitalization_grace_period_months")
    val interestCapitalizationGracePeriodMonths: java.math.BigDecimal,

    @field:JsonProperty("repayment_model")
    val repaymentModel: StudentLoanRepaymentModel,

    /* Override the `expected_payoff_date` field. Can only be set if `type` is `student`. */
    @field:JsonProperty("expected_payoff_date")
    val expectedPayoffDate: java.time.LocalDate,

    /* Override the `guarantor` field. Can only be set if `type` is `student`. */
    @field:JsonProperty("guarantor")
    val guarantor: kotlin.String,

    /* Override the `is_federal` field. Can only be set if `type` is `student`. */
    @field:JsonProperty("is_federal")
    val isFederal: kotlin.Boolean,

    /* Override the `loan_name` field. Can only be set if `type` is `student`. */
    @field:JsonProperty("loan_name")
    val loanName: kotlin.String,

    @field:JsonProperty("loan_status")
    val loanStatus: StudentLoanStatus,

    /* Override the `payment_reference_number` field. Can only be set if `type` is `student`. */
    @field:JsonProperty("payment_reference_number")
    val paymentReferenceNumber: kotlin.String,

    @field:JsonProperty("pslf_status")
    val pslfStatus: PSLFStatus,

    /* Override the `repayment_plan.description` field. Can only be set if `type` is `student`. */
    @field:JsonProperty("repayment_plan_description")
    val repaymentPlanDescription: kotlin.String,

    /* Override the `repayment_plan.type` field. Can only be set if `type` is `student`. Possible values are: `\"extended graduated\"`, `\"extended standard\"`, `\"graduated\"`, `\"income-contingent repayment\"`, `\"income-based repayment\"`, `\"interest only\"`, `\"other\"`, `\"pay as you earn\"`, `\"revised pay as you earn\"`, or `\"standard\"`. */
    @field:JsonProperty("repayment_plan_type")
    val repaymentPlanType: kotlin.String,

    /* Override the `sequence_number` field. Can only be set if `type` is `student`. */
    @field:JsonProperty("sequence_number")
    val sequenceNumber: kotlin.String,

    @field:JsonProperty("servicer_address")
    val servicerAddress: Address

) : kotlin.collections.HashMap<String, kotlin.Any>()

