package net.djvk.fireflyPlaidConnector2.transactions

import io.ktor.client.engine.mock.*
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategoryEnum
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PlaidTransactionId
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.time.ZoneOffset
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

internal class TransactionConverterTest {
    companion object {
        @JvmStatic
        fun provideConvertPollSync(): List<Arguments> {
            return listOf(
                Arguments.of(
//                    testName: String,
                    "Existing Firefly withdrawal",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    mapOf("testPlaidAccountId" to 42),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "testPlaidAccountId",
                            transactionId = "plaidTransactionId",
                            amount = -1111.22,
                        ),
                    ),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<FireflyTransactionDto>,
                    listOf(
                        FireflyTransactionDto(
                            "fireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.withdrawal,
                                amount = "1111.22",
//                                transactionJournalId = "fireflyTransactionId",
                            ).transactions.first(),
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(),
                        updates = listOf(
                            FireflyTransactionDto(
                                "fireflyTransactionId",
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.transfer,
                                    amount = "1111.22",
//                                    transactionJournalId = "fireflyTransactionId",
                                    externalId = "plaid-plaidTransactionId",
                                    destinationId = "42",
                                ).transactions.first()
                            )
                        ),
                        deletes = listOf(),
                    ),
                )
            )
        }

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
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    mapOf("testPlaidAccountId" to 42),
//                    expectedSingles: List<Transaction>,
                    singles,
//                    expectedPairs: List<Pair<Transaction, Transaction>>,
                    pairs,
                )
            )
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideConvertPollSync")
    fun convertPollSync(
        testName: String,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        plaidCreatedTxs: List<PlaidTransaction>,
        plaidUpdatedTxs: List<PlaidTransaction>,
        plaidDeletedTxs: List<PlaidTransactionId>,
        existingFireflyTxs: List<FireflyTransactionDto>,
        expectedResult: TransactionConverter.ConvertPollSyncResult,
    ) {
        runBlocking {
            val converter = TransactionConverter(
                useNameForDestination = false,
                enablePrimaryCategorization = false,
                primaryCategoryPrefix = "a",
                enableDetailedCategorization = false,
                detailedCategoryPrefix = "b",
                timeZoneString = "America/New_York",
                transferMatchWindowDays = 10L,
            )
            val actual = converter.convertPollSync(
                accountMap,
                plaidCreatedTxs,
                plaidUpdatedTxs,
                plaidDeletedTxs,
                existingFireflyTxs,
            )

            assertEquals(expectedResult, actual)
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideSortByPairs")
    fun sortByPairs(
        testName: String,
        input: List<PlaidTransaction>,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        expectedSingles: List<PlaidTransaction>,
        expectedPairs: List<Pair<PlaidTransaction, PlaidTransaction>>,
    ) {
        runBlocking {
            val converter = TransactionConverter(
                false,
                enablePrimaryCategorization = false,
                primaryCategoryPrefix = "a",
                enableDetailedCategorization = false,
                detailedCategoryPrefix = "b",
                timeZoneString = "America/New_York",
                transferMatchWindowDays = 10L,
            )
            val (actualSingles, actualPairs) = converter.sortByPairsBatched(input, accountMap)

            // Ignore order
            assertEquals(expectedSingles.toSet(), actualSingles.toSet())
            assertEquals(expectedPairs.toSet(), actualPairs.toSet())
        }
    }

    @Test
    fun getSourceKey() {
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideConvertSingleSourceAndDestination")
    fun convertSingleSourceAndDestination(
        testName: String,
        input: PlaidTransaction,
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
                transferMatchWindowDays = 10L,
            )
            val actual = converter.convertBatchSync(listOf(input), accountMap)

            assertEquals(1, actual.size)
            val tx = actual.first().tx
            assertEquals(expectedSourceId, tx.sourceId)
            assertEquals(expectedSourceName, tx.sourceName)
            assertEquals(expectedDestinationId, tx.destinationId)
            assertEquals(expectedDestinationName, tx.destinationName)
        }
    }
}