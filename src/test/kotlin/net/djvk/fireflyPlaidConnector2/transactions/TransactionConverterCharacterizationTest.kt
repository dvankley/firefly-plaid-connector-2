package net.djvk.fireflyPlaidConnector2.transactions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.ApiClient
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategory
import net.djvk.fireflyPlaidConnector2.config.properties.TransactionStyleConfig
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.testTransactionConverter
import net.djvk.fireflyPlaidConnector2.lib.testTransferMatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Pins down current behavior that the codebase review flagged, so that intentional changes to it show up as test
 * diffs rather than going unnoticed.
 */
internal class TransactionConverterCharacterizationTest {
    private val accountMap = mapOf("plaid-account" to 1)
    private val mapper = jacksonObjectMapper().apply(ApiClient.JSON_DEFAULT)
    private val converter = testTransactionConverter(
        useNameForDestination = true,
        timeZoneString = "America/New_York",
        transferMatchWindowDays = 3,
        enablePrimaryCategorization = true,
        primaryCategoryPrefix = "p-",
        enableDetailedCategorization = false,
        detailedCategoryPrefix = "d-",
        txStyle = TransactionStyleConfig(),
    )

    /**
     * A Plaid modification is sent as a full replacement split. Fields that users typically edit in Firefly
     * (category, budget, notes, bill) are sent as explicit JSON nulls, tags are replaced with the Plaid-derived
     * tags, and the transaction is marked unreconciled.
     */
    @Test
    fun `Plaid modification update sends every user-editable field`() = runBlocking {
        val target = TransactionRead(
            "transactions", "42",
            FireflyFixtures.getTransaction(
                sourceId = "1", externalId = "plaid-modified", categoryName = "Groceries", budgetId = "7",
                notes = "user note", reconciled = true, tags = listOf("user-tag"),
            ),
            ObjectLink(),
        )
        val plaidTx = PlaidFixtures.getPaymentTransaction(
            transactionId = "modified", accountId = "plaid-account", amount = 12.5,
            personalFinanceCategory = PersonalFinanceCategory("FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES"),
        )

        val update = converter.convertPlaidModification(plaidTx, target, accountMap)
        val split = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(update.toTransactionUpdate())
            .required("transactions").single()

        assertThat(update.id).isEqualTo("42")
        for (field in listOf("category_name", "category_id", "budget_id", "budget_name", "notes", "bill_id")) {
            assertThat(split.has(field)).describedAs(field).isTrue()
            assertThat(split.required(field).isNull).describedAs(field).isTrue()
        }
        assertThat(split.required("reconciled").asBoolean()).isFalse()
        assertThat(split.required("tags").map { it.asText() }).containsExactly("p-food-and-drink")
        assertThat(split.required("amount").asText()).isEqualTo("12.5")
        assertThat(split.required("source_id").asText()).isEqualTo("1")
        assertThat(split.required("external_id").asText()).isEqualTo("plaid-modified")
    }

    /**
     * An unrecognized personal finance category is rejected wherever the category is parsed. With transfer matching
     * enabled that includes every transaction, so a new Plaid category currently fails the whole batch.
     */
    @Test
    fun `unknown Plaid category fails transfer matching`() {
        val tx = PlaidFixtures.getPaymentTransaction(
            accountId = "plaid-account",
            personalFinanceCategory = PersonalFinanceCategory("NEW_PRIMARY", "NEW_PRIMARY_DETAIL"),
        )
        val normalized = listOf(PlaidFireflyTransaction.PlaidTransaction(tx, 1))

        assertFailsWith<IllegalArgumentException> {
            testTransferMatcher(transferMatchWindowDays = 3).match(normalized)
        }
    }

    @Test
    fun `unknown Plaid category passes through when transfer matching is disabled`() {
        val tx = PlaidFixtures.getPaymentTransaction(
            accountId = "plaid-account",
            personalFinanceCategory = PersonalFinanceCategory("NEW_PRIMARY", "NEW_PRIMARY_DETAIL"),
        )
        val normalized = listOf(PlaidFireflyTransaction.PlaidTransaction(tx, 1))

        assertThat(testTransferMatcher(transferMatchWindowDays = 0).match(normalized)).isEqualTo(normalized)
    }

    @Test
    fun `unknown Plaid category fails conversion when detailed tags are enabled`() = runBlocking<Unit> {
        val detailed = testTransactionConverter(
            useNameForDestination = true,
            timeZoneString = "America/New_York",
            transferMatchWindowDays = 0,
            enablePrimaryCategorization = false,
            primaryCategoryPrefix = "p-",
            enableDetailedCategorization = true,
            detailedCategoryPrefix = "d-",
            txStyle = TransactionStyleConfig(),
        )
        val tx = PlaidFixtures.getPaymentTransaction(
            accountId = "plaid-account",
            personalFinanceCategory = PersonalFinanceCategory("NEW_PRIMARY", "NEW_PRIMARY_DETAIL"),
        )

        assertFailsWith<IllegalArgumentException> { detailed.convertBatchSync(listOf(tx), accountMap) }
    }
}
