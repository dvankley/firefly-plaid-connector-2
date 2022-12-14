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
 * SandboxPublicTokenCreateRequest defines the request schema for `/sandbox/public_token/create`
 *
 * @param institutionId The ID of the institution the Item will be associated with
 * @param initialProducts The products to initially pull for the Item. May be any products that the specified `institution_id`  supports. This array may not be empty.
 * @param clientId Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body.
 * @param secret Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body.
 * @param options
 * @param userToken The user token associated with the User data is being requested for.
 */

data class SandboxPublicTokenCreateRequest(

    /* The ID of the institution the Item will be associated with */
    @field:JsonProperty("institution_id")
    val institutionId: kotlin.String,

    /* The products to initially pull for the Item. May be any products that the specified `institution_id`  supports. This array may not be empty. */
    @field:JsonProperty("initial_products")
    val initialProducts: kotlin.collections.List<Products>,

    /* Your Plaid API `client_id`. The `client_id` is required and may be provided either in the `PLAID-CLIENT-ID` header or as part of a request body. */
    @field:JsonProperty("client_id")
    val clientId: kotlin.String? = null,

    /* Your Plaid API `secret`. The `secret` is required and may be provided either in the `PLAID-SECRET` header or as part of a request body. */
    @field:JsonProperty("secret")
    val secret: kotlin.String? = null,

    @field:JsonProperty("options")
    val options: SandboxPublicTokenCreateRequestOptions? = null,

    /* The user token associated with the User data is being requested for. */
    @field:JsonProperty("user_token")
    val userToken: kotlin.String? = null

)

