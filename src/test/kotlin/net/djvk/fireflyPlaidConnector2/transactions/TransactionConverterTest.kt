package net.djvk.fireflyPlaidConnector2.transactions

import io.ktor.client.engine.mock.*
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.categories.PlaidOldCategoryCache
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock

internal class TransactionConverterTest {
    companion object {
        @JvmStatic
        fun provideConvertSingleSourceAndDestination(): List<Arguments> {
            return listOf(
                Arguments.of(
//                    testName: String,
                    "'Payment' category, payment PFC, no paymentMeta",
//                    input: Transaction,
                    PlaidFixtures.getPaymentTransaction(
                        accountId = "testPlaidAccountId"
                    ),
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    mapOf("testPlaidAccountId" to 42),
//                    expectedSourceId: String?,
                    "42",
//                    expectedSourceName: String?,
                    null,
//                    expectedDestinationId: String?,
                    null,
//                    expectedDestinationName: String?,
                    "Unknown Transfer Recipient"
                )
            )
        }
    }

    @Test
    fun convertBatch(

    ) {
    }

    @Test
    fun sortByPairs() {
    }

    @Test
    fun getSourceKey() {
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideConvertSingleSourceAndDestination")
    fun convertSingleSourceAndDestination(
        testName: String,
        input: Transaction,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        expectedSourceId: String?,
        expectedSourceName: String?,
        expectedDestinationId: String?,
        expectedDestinationName: String?,
    ) {
        runBlocking {
            val categoryCache = mock<PlaidOldCategoryCache>()
            val converter = TransactionConverter(categoryCache)
            val actual = converter.convertBatch(listOf(input), accountMap)

            assertThat(actual.size).isEqualTo(1)
            val txs = actual.first()
            assertThat(txs.transactions.size).isEqualTo(1)
            val tx = txs.transactions.first()
            assertThat(tx.sourceId).isEqualTo(expectedSourceId)
            assertThat(tx.sourceName).isEqualTo(expectedSourceName)
            assertThat(tx.destinationId).isEqualTo(expectedDestinationId)
            assertThat(tx.destinationName).isEqualTo(expectedDestinationName)
        }
    }

    @Test
    fun convertDouble() {
    }

    @Test
    fun convert() {
    }

    @Test
    fun getFireflyTransactionType() {
    }
}