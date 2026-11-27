package net.djvk.fireflyPlaidConnector2.transactions

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.testTransferMatcher
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
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
            currencyCode: String? = "USD",
        ): FireflyTransactionDto {
            val tx = FireflyFixtures.getTransaction(
                type = type,
                amount = amount,
                date = date,
                sourceId = sourceId,
                destinationId = destinationId,
                description = description,
                currencyCode = currencyCode,
            )
            val txSplit = tx.transactions.getOrNull(0) ?: throw RuntimeException("Missing transaction")
            return FireflyTransactionDto("tx-${transactionIndex++}", txSplit)
        }

        @JvmStatic
        fun provideTransferPairTestCases(): List<Arguments> {
            val plaidAcctA = "aaa"
            val fireflyAcctA = 1
            val plaidAcctB = "bbb"
            val fireflyAcctB = 2

            val pairPlaid = PlaidFireflyTransaction.Transfer.create(
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

            val pairMixedA = PlaidFireflyTransaction.Transfer.create(
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

            val pairMixedB = PlaidFireflyTransaction.Transfer.create(
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
                        personalFinanceCategory = PersonalFinanceCategoryEnum.INCOME_SALARY,
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
                    (singles + sequenceOf(pairPlaid.deposit, pairPlaid.withdrawal)).shuffled(),
                    singles + listOf(pairPlaid),
                ),
                Arguments.of(
                    "Matching Plaid Source and Firefly Destination",
                    (singles + sequenceOf(pairMixedA.deposit, pairMixedA.withdrawal)).shuffled(),
                    singles + listOf(pairMixedA),
                ),
                Arguments.of(
                    "Matching Firefly Source and Plaid Destination",
                    (singles + sequenceOf(pairMixedB.deposit, pairMixedB.withdrawal)).shuffled(),
                    singles + listOf(pairMixedB),
                ),
            )
        }
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideTransferPairTestCases")
    fun match(
        testName: String,
        input: List<PlaidFireflyTransaction>,
        expected: List<PlaidFireflyTransaction>,
    ) {
        val actual = matcher.match(input)
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected)
    }

    @Test
    fun `matches exact decimal amounts across Plaid and Firefly`() {
        val incoming = plaidCandidate("incoming", 1, -100.0)
        val differentAmount = PlaidFireflyTransaction.FireflyTransaction(
            getFireflyDto(TransactionTypeProperty.withdrawal, "100.0000000000000001", sourceId = "2")
        )
        val sameAmount = PlaidFireflyTransaction.FireflyTransaction(
            getFireflyDto(TransactionTypeProperty.withdrawal, "100.0000", sourceId = "2")
        )

        assertThat(matcher.match(listOf(incoming, differentAmount, sameAmount))).containsExactlyInAnyOrder(
            differentAmount,
            PlaidFireflyTransaction.Transfer.create(sameAmount, incoming),
        )
    }

    @Test
    fun `keeps entries with unknown currency separate`() {
        val incoming = plaidCandidate("incoming", 1, -100.0, currencyCode = null)
        val existing = PlaidFireflyTransaction.FireflyTransaction(
            getFireflyDto(TransactionTypeProperty.withdrawal, "100.00", sourceId = "2", currencyCode = null)
        )

        assertThat(matcher.match(listOf(incoming, existing))).containsExactlyInAnyOrder(incoming, existing)
    }

    @Test
    fun `keeps synced entries with conflicting currencies separate`() {
        val outgoing = plaidCandidate("outgoing", 1, 100.0)
        val synced = PlaidFireflyTransaction.MatchedTransaction(
            outgoing.plaidTransaction,
            getFireflyDto(TransactionTypeProperty.withdrawal, "100.00", sourceId = "1", currencyCode = "EUR"),
        )
        val incoming = plaidCandidate("incoming", 2, -100.0)

        assertThat(matcher.match(listOf(synced, incoming))).containsExactlyInAnyOrder(synced, incoming)
    }

    @Test
    fun `matches Plaid entries with the same unofficial currency code`() {
        val outgoing = plaidCandidate("outgoing", 1, 1.0, currencyCode = null, unofficialCurrencyCode = "XBT")
        val incoming = plaidCandidate("incoming", 2, -1.0, currencyCode = null, unofficialCurrencyCode = "XBT")

        assertThat(matcher.match(listOf(outgoing, incoming)))
            .containsExactly(PlaidFireflyTransaction.Transfer.create(outgoing, incoming))
    }

    @Test
    fun `keeps entries separate when a closest partner prefers another entry`() {
        val outgoing = plaidCandidate("outgoing", 1, 100.0)
        val laterOutgoing = plaidCandidate("later-outgoing", 1, 100.0, baseDateTime.plusMinutes(10))
        val incoming = plaidCandidate("incoming", 2, -100.0, baseDateTime.plusMinutes(1))
        val laterIncoming = plaidCandidate("later-incoming", 2, -100.0, baseDateTime.plusMinutes(3))

        assertThat(matcher.match(listOf(outgoing, laterOutgoing, incoming, laterIncoming))).containsExactlyInAnyOrder(
            PlaidFireflyTransaction.Transfer.create(outgoing, incoming),
            laterOutgoing,
            laterIncoming,
        )
    }

    @Test
    fun `warns when the closest eligible transfer candidates are tied`() {
        val outgoing = plaidCandidate("outgoing", 1, 100.0)
        val earlier = plaidCandidate("earlier", 2, -100.0, baseDateTime.minusMinutes(10))
        val later = plaidCandidate("later", 2, -100.0, baseDateTime.plusMinutes(10))
        val input = listOf(outgoing, earlier, later)
        val logger = LoggerFactory.getLogger(TransferMatcher::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            assertThat(matcher.match(input)).containsExactlyInAnyOrderElementsOf(input)
            val warnings = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
            assertThat(warnings).hasSize(1)
            assertThat(warnings.single()).contains("plaid:outgoing", "separate", "tied", "600 seconds")
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `keeps repeated transaction IDs separate`() {
        val outgoing = plaidCandidate("outgoing", 1, 100.0)
        val repeated = plaidCandidate("outgoing", 1, 100.0, baseDateTime.plusDays(1))
        val incoming = plaidCandidate("incoming", 2, -100.0)
        val laterIncoming = plaidCandidate("later-incoming", 2, -100.0, baseDateTime.plusDays(1))
        val input = listOf(outgoing, repeated, incoming, laterIncoming)

        assertThat(matcher.match(input)).containsExactlyInAnyOrderElementsOf(input)
    }

    @Test
    fun `excludes both edges of the time window`() {
        val outgoing = plaidCandidate("outgoing", 1, 100.0)
        val earlier = plaidCandidate("earlier", 2, -100.0, baseDateTime.minusDays(3))
        val later = plaidCandidate("later", 2, -100.0, baseDateTime.plusDays(3))
        val inside = plaidCandidate("inside", 2, -100.0, baseDateTime.plusDays(3).minusSeconds(1))

        assertThat(matcher.match(listOf(outgoing, earlier, later))).containsExactlyInAnyOrder(outgoing, earlier, later)
        assertThat(matcher.match(listOf(outgoing, earlier, later, inside))).containsExactlyInAnyOrder(
            earlier,
            later,
            PlaidFireflyTransaction.Transfer.create(outgoing, inside),
        )
    }

    private val matcher = testTransferMatcher(transferMatchWindowDays = matchWindowDays)

    private fun plaidCandidate(
        id: String,
        accountId: Int,
        amount: Double,
        date: OffsetDateTime = baseDateTime,
        currencyCode: String? = "USD",
        unofficialCurrencyCode: String? = null,
    ) = PlaidFireflyTransaction.PlaidTransaction(
        PlaidFixtures.getPaymentTransaction(
            transactionId = id,
            accountId = accountId.toString(),
            amount = amount,
            datetime = date,
            isoCurrencyCode = currencyCode,
            unofficialCurrencyCode = unofficialCurrencyCode,
        ),
        accountId,
    )
}
