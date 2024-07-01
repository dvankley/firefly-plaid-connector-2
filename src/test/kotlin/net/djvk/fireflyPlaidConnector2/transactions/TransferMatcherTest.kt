package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class TransferMatcherTest {
    companion object {
        val baseDateTime = OffsetDateTime.of(2022, 10, 1, 0, 0, 0, 0, ZoneOffset.ofHours(4))
        var matchWindowDays = 3L
        var transactionIndex = 0

        fun getFireflyDto(
            type: TransactionTypeProperty,
            amount: String,
            date: OffsetDateTime = baseDateTime,
            sourceId: String? = null,
            destinationId: String? = null,
            description: String = "TestTx",
        ): FireflyTransactionDto {
            val tx = FireflyFixtures.getTransaction(
                type = type,
                amount = amount,
                date = date,
                sourceId = sourceId,
                destinationId = destinationId,
                description = description,
            )
            val txSplit = tx.transactions.getOrNull(0) ?: throw RuntimeException("Missing transaction")
            return FireflyTransactionDto("tx-${transactionIndex++}", txSplit)
        }

        private fun sortListOfPairs(input: List<Pair<PlaidFireflyTransaction, PlaidFireflyTransaction>>): List<List<PlaidFireflyTransaction>> {
            return input.map { pair ->
                pair.toList().sortedBy { tx -> tx.transactionId }
            }.sortedBy { it.first().transactionId }
        }

        @JvmStatic
        fun provideTransferPairTestCases(): List<Arguments> {
            val plaidAcctA = "aaa"
            val fireflyAcctA = 1
            val plaidAcctB = "bbb"
            val fireflyAcctB = 2

            val pairPlaid = Pair(
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Plaid Transfer Source",
                        datetime = baseDateTime.minusHours(48),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctA,
                        amount = 100.0,
                    ),
                    fireflyAcctA,
                ),
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Plaid Transfer Dest",
                        datetime = baseDateTime,
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctB,
                        amount = -100.0,
                    ),
                    fireflyAcctB,
                ),
            )

            val pairMixedA = Pair(
                PlaidFireflyTransaction.FireflyTransaction(
                    getFireflyDto(
                        description = "Firefly Transfer Source",
                        date = baseDateTime.minusHours(48),
                        type = TransactionTypeProperty.withdrawal,
                        sourceId = fireflyAcctA.toString(),
                        amount = "100.00",
                    ),
                ),
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Plaid Transfer Destination",
                        datetime = baseDateTime,
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctB,
                        amount = -100.0,
                    ),
                    fireflyAcctB,
                ),
            )

            val pairMixedB = Pair(
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Plaid Transfer Source",
                        datetime = baseDateTime.minusHours(48),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctA,
                        amount = 100.0,
                    ),
                    fireflyAcctA,
                ),
                PlaidFireflyTransaction.FireflyTransaction(
                    getFireflyDto(
                        description = "Firefly Transfer Destination",
                        date = baseDateTime,
                        type = TransactionTypeProperty.deposit,
                        destinationId = fireflyAcctB.toString(),
                        amount = "100.00",
                    ),
                ),
            )

            val singles = listOf(
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Category makes this not a transfer",
                        datetime = baseDateTime,
                        personalFinanceCategory = PersonalFinanceCategoryEnum.INCOME_WAGES,
                        accountId = plaidAcctA,
                        amount = 100.0,
                    ),
                    fireflyAcctB,
                ),
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Date is not as close as the real matching transaction",
                        datetime = baseDateTime.minusHours(49),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctA,
                        amount = 100.0,
                    ),
                    fireflyAcctB,
                ),
                // Same account as matching transaction
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Can't match because the account ID is same as the destination",
                        datetime = baseDateTime,
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctB,
                        amount = 100.0,
                    ),
                    fireflyAcctB,
                ),
                // Amount does not match
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Amount does not match",
                        datetime = baseDateTime,
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctA,
                        amount = 101.0,
                    ),
                    fireflyAcctB,
                ),
                // These two transactions would match each other if their dates were a little closer together.
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Expected single because date is too far away from pair (1)",
                        datetime = baseDateTime,
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctA,
                        amount = -200.0,
                    ),
                    fireflyAcctA,
                ),
                PlaidFireflyTransaction.PlaidTransaction(
                    PlaidFixtures.getTransferTestTransaction(
                        name = "Expected single because date is too far away from pair (2)",
                        datetime = baseDateTime.minusDays(matchWindowDays).minusMinutes(1),
                        personalFinanceCategory = PersonalFinanceCategoryEnum.BANK_FEES_ATM_FEES,
                        accountId = plaidAcctB,
                        amount = 200.0,
                    ),
                    fireflyAcctB,
                ),
            )

            return listOf(
                Arguments.of(
                    "Matching Plaid Source/Dest",
                    (singles + sequenceOf(pairPlaid.first, pairPlaid.second)).shuffled(),
                    singles,
                    listOf(pairPlaid),
                ),
                Arguments.of(
                    "Matching Plaid Source and Firefly Destination",
                    (singles + sequenceOf(pairMixedA.first, pairMixedA.second)).shuffled(),
                    singles,
                    listOf(pairMixedA),
                ),
                Arguments.of(
                    "Matching Firefly Source and Plaid Destination",
                    (singles + sequenceOf(pairMixedB.first, pairMixedB.second)).shuffled(),
                    singles,
                    listOf(pairMixedB),
                ),
            )
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideTransferPairTestCases")
    fun match(
        testName: String,
        input: List<PlaidFireflyTransaction>,
        expectedSingles: List<PlaidFireflyTransaction>,
        expectedPairs: List<Pair<PlaidFireflyTransaction, PlaidFireflyTransaction>>,
    ) {
        val matcher = TransferMatcher(
            timeZoneString = "America/New_York",
            transferMatchWindowDays = matchWindowDays,
        )

        val (actualSingles, actualPairs) = matcher.match(input)
        assertIterableEquals(sortListOfPairs(expectedPairs), sortListOfPairs(actualPairs))
        assertIterableEquals(expectedSingles.sortedBy { it.transactionId }, actualSingles.sortedBy { it.transactionId })
    }
}