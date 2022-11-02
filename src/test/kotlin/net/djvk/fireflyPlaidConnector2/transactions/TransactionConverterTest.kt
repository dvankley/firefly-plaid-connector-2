package net.djvk.fireflyPlaidConnector2.transactions

import io.ktor.client.engine.mock.*
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategoryEnum
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.time.ZoneOffset

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

        @JvmStatic
        fun provideSortByPairs(): List<Arguments> {
            val baseDateTime = OffsetDateTime.of(2022, 10, 1, 0, 0, 0, 0, ZoneOffset.ofHours(4))
            val singles = listOf(
                // Single because not a transfer
                PlaidFixtures.getTransferTestTransaction(
                    datetime = baseDateTime.minusHours(18),
                    personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                    18.0,
                ),
                // Single because not a transfer, even though it has a matching amount
                PlaidFixtures.getTransferTestTransaction(
                    datetime = baseDateTime.minusHours(19),
                    personalFinanceCategory = PersonalFinanceCategoryEnum.INCOME_WAGES,
                    100.0,
                ),
                // Single because no matching amount
                PlaidFixtures.getTransferTestTransaction(
                    datetime = baseDateTime.minusHours(19),
                    personalFinanceCategory = PersonalFinanceCategoryEnum.TRANSFER_IN_ACCOUNT_TRANSFER,
                    19.0,
                ),
                // Single because while it's a transfer and has a matching amount, its timestamp is farther away than
                //  all the other candidates
                PlaidFixtures.getTransferTestTransaction(
                    datetime = baseDateTime.minusHours(20),
                    personalFinanceCategory = PersonalFinanceCategoryEnum.TRANSFER_IN_ACCOUNT_TRANSFER,
                    -100.0,
                ),
            )
            val pairs = listOf(
                Pair(
                    PlaidFixtures.getTransferTestTransaction(
                        datetime = baseDateTime.minusHours(4),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.TRANSFER_IN_ACCOUNT_TRANSFER,
                        -100.0,
                    ),
                    PlaidFixtures.getTransferTestTransaction(
                        datetime = baseDateTime.minusHours(4),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.TRANSFER_OUT_ACCOUNT_TRANSFER,
                        100.0,
                    ),
                ),
                Pair(
                    PlaidFixtures.getTransferTestTransaction(
                        datetime = baseDateTime.minusHours(6),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.TRANSFER_IN_DEPOSIT,
                        -100.0,
                    ),
                    PlaidFixtures.getTransferTestTransaction(
                        datetime = baseDateTime.minusHours(7),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.TRANSFER_OUT_WITHDRAWAL,
                        100.0,
                    ),
                ),
            )
            return listOf(
                Arguments.of(
//                    testName: String,
                    "Base case",
//                    input: List<Transaction>,
                    singles + pairs.flatMap { sequenceOf(it.first, it.second) }.shuffled(),
//                    expectedSingles: List<Transaction>,
                    singles,
//                    expectedPairs: List<Pair<Transaction, Transaction>>,
                    pairs,
                )
            )
        }
    }

    @Test
    fun convertBatch(

    ) {
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideSortByPairs")
    fun sortByPairs(
        testName: String,
        input: List<Transaction>,
        expectedSingles: List<Transaction>,
        expectedPairs: List<Pair<Transaction, Transaction>>,
    ) {
        runBlocking {
            val converter = TransactionConverter(
                false,
                enablePrimaryCategorization = false,
                primaryCategoryPrefix = "a",
                enableDetailedCategorization = false,
                detailedCategoryPrefix = "b",
                timeZoneString = "America/New_York",
            )
            val (actualSingles, actualPairs) = converter.sortByPairsBatched(input)

            assertThat(actualSingles).isEqualTo(expectedSingles)
            assertThat(actualPairs).isEqualTo(expectedPairs)
        }
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
            val converter = TransactionConverter(
                false,
                enablePrimaryCategorization = false,
                primaryCategoryPrefix = "a",
                enableDetailedCategorization = false,
                detailedCategoryPrefix = "b",
                timeZoneString = "America/New_York",
            )
            val actual = converter.convertBatchSync(listOf(input), accountMap)

            assertThat(actual.size).isEqualTo(1)
            val tx = actual.first().tx
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