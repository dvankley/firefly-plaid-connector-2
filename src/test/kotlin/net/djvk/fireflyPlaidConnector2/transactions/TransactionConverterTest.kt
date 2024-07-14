package net.djvk.fireflyPlaidConnector2.transactions

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidTransactionId
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.defaultLocalNow
import net.djvk.fireflyPlaidConnector2.lib.defaultOffsetNow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.time.ZoneId
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
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            transactionId = "plaidTransactionId",
                            amount = -1111.22,
                        ),
                    ),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "wrongTypeFireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.openingBalance,
                                amount = "2222.33",
                                sourceId = "3",
                            ), ObjectLink()
                        ),
                        // This is identical to the expected matching transaction, except for the amount.
                        TransactionRead(
                            "thing", "wrongAmountFireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.withdrawal,
                                amount = "1234.56",
                                sourceId = "2",
                            ), ObjectLink()
                        ),
                        // This is identical to the expected matching transaction, except that it's the same account
                        // as the Plaid transaction.
                        TransactionRead(
                            "thing", "wrongAccountfireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.withdrawal,
                                amount = "1111.22",
                                sourceId = "1",
                            ), ObjectLink()
                        ),
                        // This is identical to the expected matching transaction, except that it's a deposit.
                        TransactionRead(
                            "thing", "wrongTypeFireflyDepositTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                amount = "1111.22",
                                destinationId = "2",
                            ), ObjectLink()
                        ),
                        // This is the transaction that we expect to match with the Plaid transaction to be converted
                        // into a transfer.
                        TransactionRead(
                            "thing", "fireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.withdrawal,
                                amount = "1111.22",
                                sourceId = "2",
                                // The transfer matching logic attempts to find the closest (by date) matching
                                // transaction. Offset this date compared to the other Firefly transactions above so
                                // that when this one "wins" we know it's not incidental just because this one was
                                // processed first.
                                dateSubtractHours = 12
                            ), ObjectLink()
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
                                    externalId = "plaid-plaidTransactionId",
                                    sourceId = "2",
                                    destinationId = "1",
                                ).transactions.first()
                            )
                        ),
                        deletes = listOf(),
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Existing Firefly deposit",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            transactionId = "plaidTransactionId",
                            amount = 1111.22,
                        ),
                    ),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "fireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                amount = "1111.22",
                                destinationId = "2",
                            ), ObjectLink()
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
                                    externalId = "plaid-plaidTransactionId",
                                    sourceId = "1",
                                    destinationId = "2",
                                ).transactions.first()
                            )
                        ),
                        deletes = listOf(),
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Incoming Plaid Deposit and Withdrawal",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            transactionId = "plaidWithdrawalId",
                            name = "Plaid Withdrawal Tx",
                            amount = 1111.22,
                        ),
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                            transactionId = "plaidDepositId",
                            name = "Plaid Deposit Tx",
                            amount = -1111.22,
                        ),
                    ),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "unrelatedFireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                amount = "123.45",
                                destinationId = "2",
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.transfer,
                                    amount = "1111.22",
                                    externalId = "plaid-plaidDepositId",
                                    description = "Plaid Deposit Tx",
                                    sourceId = "1",
                                    destinationId = "2",
                                ).transactions.first()
                            ),
                        ),
                        updates = listOf(),
                        deletes = listOf(),
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Update without matching Firefly Transaction",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            transactionId = "plaidUpdateId",
                            amount = 1111.22,
                        ),
                    ),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "unrelatedFireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                amount = "123.45",
                                destinationId = "2",
                                externalId = "unrelated"
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(),
                        updates = listOf(),
                        deletes = listOf(),
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Update with matching Firefly Transaction",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Updated transaction name",
                            transactionId = "plaidUpdateId",
                            amount = 1111.22,
                        ),
                    ),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "updatedFireflyId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                description = "Old transaction name",
                                amount = "123.45",
                                sourceId = "1",
                                destinationName = "Unknown Transfer Recipient",
                                externalId = "plaid-plaidUpdateId",
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(),
                        updates = listOf(
                            FireflyTransactionDto(
                                "updatedFireflyId",
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.withdrawal,
                                    description = "Updated transaction name",
                                    amount = "1111.22",
                                    sourceId = "1",
                                    destinationName = "Unknown Transfer Recipient",
                                    externalId = "plaid-plaidUpdateId",
                                ).transactions.first()
                            ),
                        ),
                        deletes = listOf(),
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Delete without matching Firefly Transaction",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(
                        "deletedTransactionId",
                    ),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "updatedFireflyId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                amount = "123.45",
                                destinationId = "2",
                                externalId = "unrelated"
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(),
                        updates = listOf(),
                        deletes = listOf(),
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Delete with matching Firefly Transaction",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(
                        "deletedTransactionId",
                    ),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "deletedFireflyId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                description = "Old transaction name",
                                amount = "123.45",
                                sourceId = "1",
                                destinationName = "Unknown Transfer Recipient",
                                externalId = "plaid-deletedTransactionId",
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(),
                        updates = listOf(),
                        deletes = listOf(
                            "deletedFireflyId",
                        ),
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Create singles",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Plaid deposit",
                            transactionId = "plaidDepositId",
                            amount = -1111.22,
                        ),
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Plaid withdrawal",
                            transactionId = "plaidWithdrawalId",
                            amount = 123.45,
                        ),
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Plaid withdrawal with matching FF",
                            transactionId = "plaidWithdrawalWithMatchingFfId",
                            amount = 234.56,
                        ),
                    ),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf<TransactionRead>(
                        TransactionRead(
                            "thing", "matchingFireflyTxId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.withdrawal,
                                description = "Matching Firefly Tx",
                                amount = "234.56",
                                sourceId = "1",
                                destinationName = "Unknown Transfer Recipient",
                                externalId = "plaid-plaidWithdrawalWithMatchingFfId",
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.deposit,
                                    description = "Plaid deposit",
                                    amount = "1111.22",
                                    sourceName = "Unknown Transfer Source",
                                    destinationId = "1",
                                    externalId = "plaid-plaidDepositId",
                                ).transactions.first()
                            ),
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.withdrawal,
                                    description = "Plaid withdrawal",
                                    amount = "123.45",
                                    sourceId = "1",
                                    destinationName = "Unknown Transfer Recipient",
                                    externalId = "plaid-plaidWithdrawalId",
                                ).transactions.first()
                            ),
                        ),
                        updates = listOf(),
                        deletes = listOf(),
                    ),
                ),

                // This test case creates Plaid transactions with various combinations of date fields present, each
                // field having a slightly different offset from our "default" date. We then validate that the expected
                // offset was used to create the Firefly transactions.
                Arguments.of(
//                    testName: String,
                    "Authorized time is preferred over posted, and dateTime is preferred over date",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Tx with authorized date and dateTime",
                            transactionId = "txWithAuthorizedDateAndDateTime",
                            amount = -1111.11,
                            date = defaultLocalNow,
                            datetime = defaultOffsetNow.minusDays(1),
                            authorizedDate = defaultLocalNow.minusDays(2),
                            authorizedDatetime = defaultOffsetNow.minusDays(3),
                        ),
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Tx with authorized date",
                            transactionId = "txWithAuthorizedDate",
                            amount = -1111.22,
                            date = defaultLocalNow,
                            datetime = defaultOffsetNow.minusDays(1),
                            authorizedDate = defaultLocalNow.minusDays(2),
                        ),
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Tx with posted date and dateTime",
                            transactionId = "txWithPostedDateAndDateTime",
                            amount = -1111.33,
                            date = defaultLocalNow,
                            datetime = defaultOffsetNow.minusDays(1),
                        ),
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Tx with posted date",
                            transactionId = "txWithPostedDate",
                            amount = -1111.44,
                            date = defaultLocalNow,
                        ),
                    ),
//                    plaidUpdatedTxs: List<PlaidTransaction>,
                    listOf<PlaidTransaction>(),
//                    plaidDeletedTxs: List<PlaidTransactionId>,
                    listOf<PlaidTransactionId>(),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf<TransactionRead>(),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.deposit,
                                    description = "Tx with authorized date and dateTime",
                                    amount = "1111.11",
                                    sourceName = "Unknown Transfer Source",
                                    destinationId = "1",
                                    externalId = "plaid-txWithAuthorizedDateAndDateTime",
                                    date = defaultOffsetNow.minusDays(3),
                                    processDate = defaultOffsetNow.minusDays(1),
                                ).transactions.first()
                            ),
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.deposit,
                                    description = "Tx with authorized date",
                                    amount = "1111.22",
                                    sourceName = "Unknown Transfer Source",
                                    destinationId = "1",
                                    externalId = "plaid-txWithAuthorizedDate",
                                    date = defaultOffsetNow.minusDays(2),
                                    processDate = defaultOffsetNow.minusDays(1),
                                ).transactions.first()
                            ),
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.deposit,
                                    description = "Tx with posted date and dateTime",
                                    amount = "1111.33",
                                    sourceName = "Unknown Transfer Source",
                                    destinationId = "1",
                                    externalId = "plaid-txWithPostedDateAndDateTime",
                                    date = defaultOffsetNow.minusDays(1),
                                    processDate = defaultOffsetNow.minusDays(1),
                                ).transactions.first()
                            ),
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.deposit,
                                    description = "Tx with posted date",
                                    amount = "1111.44",
                                    sourceName = "Unknown Transfer Source",
                                    destinationId = "1",
                                    externalId = "plaid-txWithPostedDate",
                                    date = defaultOffsetNow,
                                    processDate = defaultOffsetNow,
                                ).transactions.first()
                            ),
                        ),
                        updates = listOf(),
                        deletes = listOf(),
                    ),
                ),
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

        fun convertCreates(
            converter: TransactionConverter,
            poll: Boolean,
            inputPlaidTxs: List<PlaidTransaction>,
            accountMap: Map<PlaidAccountId, FireflyAccountId>,
        ): List<FireflyTransactionDto> {
            return runBlocking {
                if (poll) {
                    val result = converter.convertPollSync(
                        accountMap,
                        inputPlaidTxs,
                        listOf(),
                        listOf(),
                        listOf(),
                    )
                    // Since this isn't passing-in any existing Plaid transactions, these should always be empty
                    assertThat(result.deletes).isEmpty()
                    assertThat(result.updates).isEmpty()
                    return@runBlocking result.creates
                } else {
                    return@runBlocking converter.convertBatchSync(
                        inputPlaidTxs,
                        accountMap,
                    )
                }
            }
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
        existingFireflyTxs: List<TransactionRead>,
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

    @ParameterizedTest(name = "poll mode = {0}")
    @ValueSource(booleans = [true, false])
    fun convertWithCategorizationEnabledAddsExpectedTags(poll: Boolean) {
        val converter = TransactionConverter(
            false,
            enablePrimaryCategorization = true,
            primaryCategoryPrefix = "pcat-",
            enableDetailedCategorization = true,
            detailedCategoryPrefix = "dcat-",
            timeZoneString = "America/New_York",
            transferMatchWindowDays = 10L,
        )

        val plaidTxWithCategory = PlaidFixtures.getPaymentTransaction(
            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            name = "Plaid transaction with categories",
            transactionId = "plaidIdWithCats",
            amount = 123.45,
            personalFinanceCategory = PersonalFinanceCategoryEnum.TRAVEL_FLIGHTS.toPersonalFinanceCategory(),
        )

        val plaidTxWithoutCategory = PlaidFixtures.getPaymentTransaction(
            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            name = "Plaid transaction without categories",
            transactionId = "plaidIdWithoutCats",
            amount = 234.56,
            personalFinanceCategory = null,
        )

        val expectedFfTxWithTags = FireflyTransactionDto(
            null,
            FireflyFixtures.getTransaction(
                type = TransactionTypeProperty.withdrawal,
                description = "Plaid transaction with categories",
                amount = "123.45",
                sourceId = "1",
                destinationName = "Unknown Payment Recipient",
                tags = listOf("pcat-travel", "dcat-flights"),
                externalId = "plaid-plaidIdWithCats",
            ).transactions.first()
        )

        val expectedFfTxWithoutTags = FireflyTransactionDto(
            null,
            FireflyFixtures.getTransaction(
                type = TransactionTypeProperty.withdrawal,
                description = "Plaid transaction without categories",
                amount = "234.56",
                sourceId = "1",
                destinationName = "Unknown",
                tags = listOf(),
                externalId = "plaid-plaidIdWithoutCats",
            ).transactions.first()
        )

        val actual = convertCreates(
            converter = converter,
            poll = poll,
            inputPlaidTxs = listOf(plaidTxWithCategory, plaidTxWithoutCategory),
            accountMap = PlaidFixtures.getStandardAccountMapping()
        )
        assertThat(actual).containsExactlyInAnyOrder(expectedFfTxWithTags, expectedFfTxWithoutTags)
    }

    @ParameterizedTest(name = "poll mode = {0}")
    @ValueSource(booleans = [true, false])
    fun convertPollSyncThrowsWhenFireflyAccountIdNotFound(poll: Boolean) {
        val converter = TransactionConverter(
            useNameForDestination = false,
            enablePrimaryCategorization = false,
            primaryCategoryPrefix = "a",
            enableDetailedCategorization = false,
            detailedCategoryPrefix = "b",
            timeZoneString = "America/New_York",
            transferMatchWindowDays = 10L,
        )

        val plaidTx = PlaidFixtures.getPaymentTransaction(
            accountId = "unknownAccountId",
            name = "Plaid transaction",
            transactionId = "plaidId",
            amount = 123.45,
        )

        assertThatThrownBy {
            convertCreates(
                converter = converter,
                poll = poll,
                inputPlaidTxs = listOf(plaidTx),
                accountMap = PlaidFixtures.getStandardAccountMapping()
            )
        }
            .hasMessageContaining("Can not match Plaid transactions from accounts not mapped to a Firefly account id")
    }
}
