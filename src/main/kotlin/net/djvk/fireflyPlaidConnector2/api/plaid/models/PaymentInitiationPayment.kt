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
 * PaymentInitiationPayment defines a payment initiation payment
 *
 * @param paymentId The ID of the payment. Like all Plaid identifiers, the `payment_id` is case sensitive.
 * @param amount
 * @param status
 * @param recipientId The ID of the recipient
 * @param reference A reference for the payment.
 * @param lastStatusUpdate The date and time of the last time the `status` was updated, in IS0 8601 format
 * @param bacs
 * @param iban The International Bank Account Number (IBAN) for the sender, if specified in the `/payment_initiation/payment/create` call.
 * @param adjustedReference The value of the reference sent to the bank after adjustment to pass bank validation rules.
 * @param schedule
 * @param refundDetails
 * @param refundIds Refund IDs associated with the payment.
 * @param walletId The EMI (E-Money Institution) wallet that this payment is associated with, if any. This wallet is used as an intermediary account to enable Plaid to reconcile the settlement of funds for Payment Initiation requests.
 * @param scheme
 * @param adjustedScheme
 * @param consentId The payment consent ID that this payment was initiated with. Is present only when payment was initiated using the payment consent.
 */

data class PaymentInitiationPayment(

    /* The ID of the payment. Like all Plaid identifiers, the `payment_id` is case sensitive. */
    @field:JsonProperty("payment_id")
    val paymentId: kotlin.String,

    @field:JsonProperty("amount")
    val amount: PaymentAmount,

    @field:JsonProperty("status")
    val status: PaymentInitiationPaymentStatus,

    /* The ID of the recipient */
    @field:JsonProperty("recipient_id")
    val recipientId: kotlin.String,

    /* A reference for the payment. */
    @field:JsonProperty("reference")
    val reference: kotlin.String,

    /* The date and time of the last time the `status` was updated, in IS0 8601 format */
    @field:JsonProperty("last_status_update")
    val lastStatusUpdate: java.time.OffsetDateTime,

    @field:JsonProperty("bacs")
    val bacs: SenderBACSNullable?,

    /* The International Bank Account Number (IBAN) for the sender, if specified in the `/payment_initiation/payment/create` call. */
    @field:JsonProperty("iban")
    val iban: kotlin.String?,

    /* The value of the reference sent to the bank after adjustment to pass bank validation rules. */
    @field:JsonProperty("adjusted_reference")
    val adjustedReference: kotlin.String? = null,

    @field:JsonProperty("schedule")
    val schedule: ExternalPaymentScheduleGet? = null,

    @field:JsonProperty("refund_details")
    val refundDetails: ExternalPaymentRefundDetails? = null,

    /* Refund IDs associated with the payment. */
    @field:JsonProperty("refund_ids")
    val refundIds: kotlin.collections.List<kotlin.String>? = null,

    /* The EMI (E-Money Institution) wallet that this payment is associated with, if any. This wallet is used as an intermediary account to enable Plaid to reconcile the settlement of funds for Payment Initiation requests. */
    @field:JsonProperty("wallet_id")
    val walletId: kotlin.String? = null,

    @field:JsonProperty("scheme")
    val scheme: PaymentScheme? = null,

    @field:JsonProperty("adjusted_scheme")
    val adjustedScheme: PaymentScheme? = null,

    /* The payment consent ID that this payment was initiated with. Is present only when payment was initiated using the payment consent. */
    @field:JsonProperty("consent_id")
    val consentId: kotlin.String? = null

) : kotlin.collections.HashMap<String, kotlin.Any>()

