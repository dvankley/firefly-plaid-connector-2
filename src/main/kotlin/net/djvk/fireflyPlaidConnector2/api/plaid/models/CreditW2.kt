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
 * W2 is an object that represents income data taken from a W2 tax document.
 *
 * @param documentMetadata
 * @param documentId An identifier of the document referenced by the document metadata.
 * @param employer
 * @param employee
 * @param taxYear The tax year of the W2 document.
 * @param employerIdNumber An employee identification number or EIN.
 * @param wagesTipsOtherComp Wages from tips and other compensation.
 * @param federalIncomeTaxWithheld Federal income tax withheld for the tax year.
 * @param socialSecurityWages Wages from social security.
 * @param socialSecurityTaxWithheld Social security tax withheld for the tax year.
 * @param medicareWagesAndTips Wages and tips from medicare.
 * @param medicareTaxWithheld Medicare tax withheld for the tax year.
 * @param socialSecurityTips Tips from social security.
 * @param allocatedTips Allocated tips.
 * @param box9 Contents from box 9 on the W2.
 * @param dependentCareBenefits Dependent care benefits.
 * @param nonqualifiedPlans Nonqualified plans.
 * @param box12
 * @param statutoryEmployee Statutory employee.
 * @param retirementPlan Retirement plan.
 * @param thirdPartySickPay Third party sick pay.
 * @param other Other.
 * @param stateAndLocalWages
 */

data class CreditW2(

    @field:JsonProperty("document_metadata")
    val documentMetadata: CreditDocumentMetadata,

    /* An identifier of the document referenced by the document metadata. */
    @field:JsonProperty("document_id")
    val documentId: kotlin.String,

    @field:JsonProperty("employer")
    val employer: CreditPayStubEmployer,

    @field:JsonProperty("employee")
    val employee: CreditPayStubEmployee,

    /* The tax year of the W2 document. */
    @field:JsonProperty("tax_year")
    val taxYear: kotlin.String?,

    /* An employee identification number or EIN. */
    @field:JsonProperty("employer_id_number")
    val employerIdNumber: kotlin.String?,

    /* Wages from tips and other compensation. */
    @field:JsonProperty("wages_tips_other_comp")
    val wagesTipsOtherComp: kotlin.String?,

    /* Federal income tax withheld for the tax year. */
    @field:JsonProperty("federal_income_tax_withheld")
    val federalIncomeTaxWithheld: kotlin.String?,

    /* Wages from social security. */
    @field:JsonProperty("social_security_wages")
    val socialSecurityWages: kotlin.String?,

    /* Social security tax withheld for the tax year. */
    @field:JsonProperty("social_security_tax_withheld")
    val socialSecurityTaxWithheld: kotlin.String?,

    /* Wages and tips from medicare. */
    @field:JsonProperty("medicare_wages_and_tips")
    val medicareWagesAndTips: kotlin.String?,

    /* Medicare tax withheld for the tax year. */
    @field:JsonProperty("medicare_tax_withheld")
    val medicareTaxWithheld: kotlin.String?,

    /* Tips from social security. */
    @field:JsonProperty("social_security_tips")
    val socialSecurityTips: kotlin.String?,

    /* Allocated tips. */
    @field:JsonProperty("allocated_tips")
    val allocatedTips: kotlin.String?,

    /* Contents from box 9 on the W2. */
    @field:JsonProperty("box_9")
    val box9: kotlin.String?,

    /* Dependent care benefits. */
    @field:JsonProperty("dependent_care_benefits")
    val dependentCareBenefits: kotlin.String?,

    /* Nonqualified plans. */
    @field:JsonProperty("nonqualified_plans")
    val nonqualifiedPlans: kotlin.String?,

    @field:JsonProperty("box_12")
    val box12: kotlin.collections.List<W2Box12>,

    /* Statutory employee. */
    @field:JsonProperty("statutory_employee")
    val statutoryEmployee: kotlin.String?,

    /* Retirement plan. */
    @field:JsonProperty("retirement_plan")
    val retirementPlan: kotlin.String?,

    /* Third party sick pay. */
    @field:JsonProperty("third_party_sick_pay")
    val thirdPartySickPay: kotlin.String?,

    /* Other. */
    @field:JsonProperty("other")
    val other: kotlin.String?,

    @field:JsonProperty("state_and_local_wages")
    val stateAndLocalWages: kotlin.collections.List<W2StateAndLocalWages>

) : kotlin.collections.HashMap<String, kotlin.Any>()

