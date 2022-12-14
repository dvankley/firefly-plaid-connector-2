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
 * @param ruleGroupId ID of the rule group under which the rule must be stored. Either this field or rule_group_title is mandatory.
 * @param trigger
 * @param triggers
 * @param actions
 * @param createdAt
 * @param updatedAt
 * @param description
 * @param ruleGroupTitle Title of the rule group under which the rule must be stored. Either this field or rule_group_id is mandatory.
 * @param order
 * @param active Whether or not the rule is even active. Default is true.
 * @param strict If the rule is set to be strict, ALL triggers must hit in order for the rule to fire. Otherwise, just one is enough. Default value is true.
 * @param stopProcessing If this value is true and the rule is triggered, other rules  after this one in the group will be skipped. Default value is false.
 */

data class Rule(

    @field:JsonProperty("title")
    val title: kotlin.String,

    /* ID of the rule group under which the rule must be stored. Either this field or rule_group_title is mandatory. */
    @field:JsonProperty("rule_group_id")
    val ruleGroupId: kotlin.String,

    @field:JsonProperty("trigger")
    val trigger: RuleTriggerType,

    @field:JsonProperty("triggers")
    val triggers: kotlin.collections.List<RuleTrigger>,

    @field:JsonProperty("actions")
    val actions: kotlin.collections.List<RuleAction>,

    @field:JsonProperty("created_at")
    val createdAt: java.time.OffsetDateTime? = null,

    @field:JsonProperty("updated_at")
    val updatedAt: java.time.OffsetDateTime? = null,

    @field:JsonProperty("description")
    val description: kotlin.String? = null,

    /* Title of the rule group under which the rule must be stored. Either this field or rule_group_id is mandatory. */
    @field:JsonProperty("rule_group_title")
    val ruleGroupTitle: kotlin.String? = null,

    @field:JsonProperty("order")
    val order: kotlin.Int? = null,

    /* Whether or not the rule is even active. Default is true. */
    @field:JsonProperty("active")
    val active: kotlin.Boolean? = true,

    /* If the rule is set to be strict, ALL triggers must hit in order for the rule to fire. Otherwise, just one is enough. Default value is true. */
    @field:JsonProperty("strict")
    val strict: kotlin.Boolean? = null,

    /* If this value is true and the rule is triggered, other rules  after this one in the group will be skipped. Default value is false. */
    @field:JsonProperty("stop_processing")
    val stopProcessing: kotlin.Boolean? = false

)

