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
 * Valid account subtypes for investment accounts. For a list containing descriptions of each subtype, see [Account schemas](https://plaid.com/docs/api/accounts/#StandaloneAccountType-investment).
 *
 * Values: _529,_401a,_401k,_403b,_457b,brokerage,cashIsa,cryptoExchange,educationSavingsAccount,fixedAnnuity,gic,healthReimbursementArrangement,hsa,ira,isa,keogh,lif,lifeInsurance,lira,lrif,lrsp,mutualFund,nonMinusTaxableBrokerageAccount,other,otherAnnuity,otherInsurance,pension,prif,profitSharingPlan,qshr,rdsp,resp,retirement,rlif,roth,roth401k,rrif,rrsp,sarsep,sepIra,simpleIra,sipp,stockPlan,tfsa,trust,ugma,utma,variableAnnuity,all
 */

enum class InvestmentAccountSubtype(val value: kotlin.String) {

    @JsonProperty(value = "529")
    _529("529"),

    @JsonProperty(value = "401a")
    _401a("401a"),

    @JsonProperty(value = "401k")
    _401k("401k"),

    @JsonProperty(value = "403B")
    _403b("403B"),

    @JsonProperty(value = "457b")
    _457b("457b"),

    @JsonProperty(value = "brokerage")
    brokerage("brokerage"),

    @JsonProperty(value = "cash isa")
    cashIsa("cash isa"),

    @JsonProperty(value = "crypto exchange")
    cryptoExchange("crypto exchange"),

    @JsonProperty(value = "education savings account")
    educationSavingsAccount("education savings account"),

    @JsonProperty(value = "fixed annuity")
    fixedAnnuity("fixed annuity"),

    @JsonProperty(value = "gic")
    gic("gic"),

    @JsonProperty(value = "health reimbursement arrangement")
    healthReimbursementArrangement("health reimbursement arrangement"),

    @JsonProperty(value = "hsa")
    hsa("hsa"),

    @JsonProperty(value = "ira")
    ira("ira"),

    @JsonProperty(value = "isa")
    isa("isa"),

    @JsonProperty(value = "keogh")
    keogh("keogh"),

    @JsonProperty(value = "lif")
    lif("lif"),

    @JsonProperty(value = "life insurance")
    lifeInsurance("life insurance"),

    @JsonProperty(value = "lira")
    lira("lira"),

    @JsonProperty(value = "lrif")
    lrif("lrif"),

    @JsonProperty(value = "lrsp")
    lrsp("lrsp"),

    @JsonProperty(value = "mutual fund")
    mutualFund("mutual fund"),

    @JsonProperty(value = "non-taxable brokerage account")
    nonMinusTaxableBrokerageAccount("non-taxable brokerage account"),

    @JsonProperty(value = "other")
    other("other"),

    @JsonProperty(value = "other annuity")
    otherAnnuity("other annuity"),

    @JsonProperty(value = "other insurance")
    otherInsurance("other insurance"),

    @JsonProperty(value = "pension")
    pension("pension"),

    @JsonProperty(value = "prif")
    prif("prif"),

    @JsonProperty(value = "profit sharing plan")
    profitSharingPlan("profit sharing plan"),

    @JsonProperty(value = "qshr")
    qshr("qshr"),

    @JsonProperty(value = "rdsp")
    rdsp("rdsp"),

    @JsonProperty(value = "resp")
    resp("resp"),

    @JsonProperty(value = "retirement")
    retirement("retirement"),

    @JsonProperty(value = "rlif")
    rlif("rlif"),

    @JsonProperty(value = "roth")
    roth("roth"),

    @JsonProperty(value = "roth 401k")
    roth401k("roth 401k"),

    @JsonProperty(value = "rrif")
    rrif("rrif"),

    @JsonProperty(value = "rrsp")
    rrsp("rrsp"),

    @JsonProperty(value = "sarsep")
    sarsep("sarsep"),

    @JsonProperty(value = "sep ira")
    sepIra("sep ira"),

    @JsonProperty(value = "simple ira")
    simpleIra("simple ira"),

    @JsonProperty(value = "sipp")
    sipp("sipp"),

    @JsonProperty(value = "stock plan")
    stockPlan("stock plan"),

    @JsonProperty(value = "tfsa")
    tfsa("tfsa"),

    @JsonProperty(value = "trust")
    trust("trust"),

    @JsonProperty(value = "ugma")
    ugma("ugma"),

    @JsonProperty(value = "utma")
    utma("utma"),

    @JsonProperty(value = "variable annuity")
    variableAnnuity("variable annuity"),

    @JsonProperty(value = "all")
    all("all");

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
        fun encode(data: kotlin.Any?): kotlin.String? = if (data is InvestmentAccountSubtype) "$data" else null

        /**
         * Returns a valid [InvestmentAccountSubtype] for [data], null otherwise.
         */
        fun decode(data: kotlin.Any?): InvestmentAccountSubtype? = data?.let {
            val normalizedData = "$it".lowercase()
            values().firstOrNull { value ->
                it == value || normalizedData == "$value".lowercase()
            }
        }
    }
}

