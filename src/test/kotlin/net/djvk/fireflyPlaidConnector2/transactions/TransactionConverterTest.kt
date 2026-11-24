package net.djvk.fireflyPlaidConnector2.transactions

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.CounterpartyType
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Location
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionCode
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionCounterparty
import net.djvk.fireflyPlaidConnector2.config.properties.TransactionStyleConfig
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.defaultLocalNow
import net.djvk.fireflyPlaidConnector2.lib.defaultOffsetNow
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter.TransferUpdate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction

internal class TransactionConverterTest {
    companion object {
        val defaultStyle = TransactionStyleConfig(null)

        @JvmStatic
        fun provideConvertPollSync(): List<Arguments> {
            return listOf(
                Arguments.of(
//                    testName: String,
                    "Existing Firefly withdrawal with ineligible competitors",
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
                                currencyCode = "USD",
                            ), ObjectLink()
                        ),
                        // This deposit shares the matching withdrawal's timestamp. Both already exist in Firefly.
                        TransactionRead(
                            "thing", "existingFireflyDepositTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                amount = "1111.22",
                                destinationId = "3",
                                currencyCode = "USD",
                                dateSubtractHours = 12,
                            ), ObjectLink()
                        ),
                        TransactionRead(
                            "thing", "otherCurrencyFireflyWithdrawalTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.withdrawal,
                                amount = "1111.22",
                                sourceId = "2",
                                currencyCode = "EUR",
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
                                currencyCode = "USD",
                                externalId = "plaid-existingWithdrawal",
                                dateSubtractHours = 12
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(),
                        updates = listOf(
                            TransferUpdate(
                                FireflyTransactionDto(
                                    "fireflyTransactionId",
                                    FireflyFixtures.getTransaction(
                                        type = TransactionTypeProperty.transfer,
                                        amount = "1111.22",
                                        externalId = "plaid-plaidTransactionId",
                                        internalReference = "plaid-transfer-leg-existingWithdrawal",
                                        sourceId = "2",
                                        destinationId = "1",
                                        currencyCode = "USD",
                                    ).transactions.first()
                                ),
                                incomingPlaidTransactionId = "plaidTransactionId",
                            )
                        ),
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
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf(
                        TransactionRead(
                            "thing", "fireflyTransactionId",
                            FireflyFixtures.getTransaction(
                                type = TransactionTypeProperty.deposit,
                                amount = "1111.22",
                                destinationId = "2",
                                currencyCode = "USD",
                            ), ObjectLink()
                        ),
                    ),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(),
                        updates = listOf(
                            TransferUpdate(
                                FireflyTransactionDto(
                                    "fireflyTransactionId",
                                    FireflyFixtures.getTransaction(
                                        type = TransactionTypeProperty.transfer,
                                        amount = "1111.22",
                                        externalId = "plaid-plaidTransactionId",
                                        sourceId = "1",
                                        destinationId = "2",
                                        currencyCode = "USD",
                                    ).transactions.first()
                                ),
                                incomingPlaidTransactionId = "plaidTransactionId",
                            )
                        ),
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
                                    internalReference = "plaid-transfer-leg-plaidWithdrawalId",
                                    description = "Plaid Deposit Tx",
                                    sourceId = "1",
                                    destinationId = "2",
                                ).transactions.first(),
                            ),
                        ),
                        updates = listOf(),
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
                    ),
                ),
                Arguments.of(
//                    testName: String,
                    "Populates expected miscellaneous fields",
//                    accountMap: Map<PlaidAccountId, FireflyAccountId>,
                    PlaidFixtures.getStandardAccountMapping(),
//                    plaidCreatedTxs: List<PlaidTransaction>,
                    listOf(
                        PlaidFixtures.getPaymentTransaction(
                            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            name = "Transaction Name",
                            originalDescription = "Transaction Orig Desc",
                            transactionId = "txWithAllMiscFieldsPopulated",
                            amount = 1111.11,
                            merchantName = "Test Merchant",
                            accountOwner = "Test Account Owner",
                            transactionCode = TransactionCode.purchase,
                            logoUrl = "https://example.org/logo.png",
                            personalFinanceCategoryIconUrl = "https://example.org/category.png",
                            counterparties = listOf(
                                TransactionCounterparty(
                                    name = "Example Merchant",
                                    type = CounterpartyType.merchant,
                                    website = "merchant.example.org",
                                    logoUrl = "https://example.org/logo_merchant.png",
                                    entityId = "merchantCounterpartyId1",
                                    confidenceLevel = "HIGH",
                                ),
                                TransactionCounterparty(
                                    name = "Example Marketplace",
                                    type = CounterpartyType.marketplace,
                                    website = "marketplace.example.org",
                                    logoUrl = "https://example.org/logo_marketplace.png",
                                    entityId = "marketplaceCounterpartyId1",
                                    confidenceLevel = "HIGH",
                                )
                            ),
                            merchantEntityId = "abc123",
                            website = "example.org",
                            location = Location(
                                address = "14400 State Hwy Z",
                                city = "St Robert",
                                region = "MO",
                                postalCode = "65584",
                                country = "US",
                                lat = 37.8291789099181,
                                lon = -92.10510834805055,
                                storeNumber = "101",
                            ),
                        ),
                    ),
//                    existingFireflyTxs: List<TransactionRead>,
                    listOf<TransactionRead>(),
//                    expectedResult: TransactionConverter.ConvertPollSyncResult,
                    TransactionConverter.ConvertPollSyncResult(
                        creates = listOf(
                            FireflyTransactionDto(
                                null,
                                FireflyFixtures.getTransaction(
                                    type = TransactionTypeProperty.withdrawal,
                                    description = "Test Merchant: Transaction Orig Desc",
                                    amount = "1111.11",
                                    destinationName = "Test Merchant",
                                    sourceId = "1",
                                    externalId = "plaid-txWithAllMiscFieldsPopulated",
                                    date = defaultOffsetNow,
                                    processDate = defaultOffsetNow,
                                    latitude = 37.8291789099181,
                                    longitude = -92.10510834805055,
                                    externalUrl = "https://example.org"
                                ).transactions.first()
                            ),
                        ),
                        updates = listOf(),
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
                    )
                    // Since this isn't passing-in any existing Plaid transactions, these should always be empty
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

        @JvmStatic
        fun provideDescriptionCases(): List<Arguments> {
            return listOf(
                Arguments.of(
                    "Expression can access fields from the Plaid transaction",
                    TransactionStyleConfig("transaction.name + ': ' + transaction.originalDescription"),
                    "Name", // Plaid "name"
                    "Merchant", // Plaid "merchantName"
                    "Desc", // Plaid "originalDescription"
                    "Name: Desc", // Expected FF description
                ),
                Arguments.of(
                    "Demonstration of the ternary operator to conditionally append a string",
                    TransactionStyleConfig("#merchantAndDescription + (transaction.amount > 100 ? ' (expensive!)' : '')"),
                    "Name", // Plaid "name"
                    "Merchant", // Plaid "merchantName"
                    "Desc", // Plaid "originalDescription"
                    "Merchant: Desc (expensive!)", // Expected FF description
                ),
                Arguments.of(
                    "Demonstration of the Elvis operator to fallback to a non-null value",
                    TransactionStyleConfig("transaction.merchantName?:transaction.name + ': ' + transaction.originalDescription"),
                    "Name", // Plaid "name"
                    null, // Plaid "merchantName"
                    "Desc", // Plaid "originalDescription"
                    "Name: Desc", // Expected FF description
                ),
                Arguments.of(
                    "Demonstration of multiple chained elvis operators",
                    TransactionStyleConfig("transaction.originalDescription ?: transaction.merchantName ?: transaction.name"),
                    "Name", // Plaid "name"
                    null, // Plaid "merchantName"
                    null, // Plaid "originalDescription"
                    "Name", // Expected FF description
                ),
                Arguments.of(
                    "#merchantAndDescription uses merchant, if available, followed by originalDescription",
                    TransactionStyleConfig("#merchantAndDescription"),
                    "unused", // Plaid "name"
                    "Merchant", // Plaid "merchantName"
                    "OriginalDesc", // Plaid "originalDescription"
                    "Merchant: OriginalDesc", // Expected FF description
                ),
                Arguments.of(
                    "null expression is same as merchantAndDescription",
                    TransactionStyleConfig(null),
                    "unused", // Plaid "name"
                    "Merchant", // Plaid "merchantName"
                    "OriginalDesc", // Plaid "originalDescription"
                    "Merchant: OriginalDesc", // Expected FF description
                ),
                Arguments.of(
                    "empty expression is same as merchantAndDescription",
                    TransactionStyleConfig("  "), // (Add a couple spaces to ensure we're trimming)
                    "unused", // Plaid "name"
                    "Merchant", // Plaid "merchantName"
                    "OriginalDesc", // Plaid "originalDescription"
                    "Merchant: OriginalDesc", // Expected FF description
                ),
                Arguments.of(
                    "spaces are trimmed from the expression",
                    TransactionStyleConfig("  #merchantAndDescription  "),
                    "unused", // Plaid "name"
                    "Merchant", // Plaid "merchantName"
                    "OriginalDesc", // Plaid "originalDescription"
                    "Merchant: OriginalDesc", // Expected FF description
                ),
                Arguments.of(
                    "#merchantAndDescription uses name if merchant is not available, followed by originalDescription",
                    TransactionStyleConfig("#merchantAndDescription"),
                    "Name", // Plaid "name"
                    null, // Plaid "merchantName"
                    "OriginalDesc", // Plaid "originalDescription"
                    "Name: OriginalDesc", // Expected FF description
                ),
                Arguments.of(
                    "#merchantAndDescription does not include originalDescription if it's not available",
                    TransactionStyleConfig("#merchantAndDescription"),
                    "Name", // Plaid "name"
                    null, // Plaid "merchantName"
                    null, // Plaid "originalDescription"
                    "Name", // Expected FF description
                ),
                Arguments.of(
                    "#merchantNameWithFallback is available and is set to merchant name when available",
                    TransactionStyleConfig("#merchantNameWithFallback"),
                    "Name", // Plaid "name"
                    "Contoso", // Plaid "merchantName"
                    "unused", // Plaid "originalDescription"
                    "Contoso", // Expected FF description
                ),
                Arguments.of(
                    "#merchantNameWithFallback is available and is set to name when merchant is not available",
                    TransactionStyleConfig("#merchantNameWithFallback"),
                    "Name", // Plaid "name"
                    null, // Plaid "merchantName"
                    "unused", // Plaid "originalDescription"
                    "Name", // Expected FF description
                ),
                Arguments.of(
                    "if the expression references a null field, the string \"null\" is used",
                    TransactionStyleConfig("transaction.merchantName + ': ' + transaction.originalDescription"),
                    "unused", // Plaid "name"
                    null, // Plaid "merchantName"
                    "Desc", // Plaid "originalDescription"
                    "null: Desc", // Expected FF description
                ),
                Arguments.of(
                    "if the expression results in null, the default description is used",
                    TransactionStyleConfig("transaction.merchantName"),
                    "Name", // Plaid "name"
                    null, // Plaid "merchantName"
                    "Desc", // Plaid "originalDescription"
                    "Name: Desc", // Expected FF description
                ),
                Arguments.of(
                    "if the expression is invalid, the default description is used",
                    TransactionStyleConfig("this-is-invalid"),
                    "Name", // Plaid "name"
                    null, // Plaid "merchantName"
                    "Desc", // Plaid "originalDescription"
                    "Name: Desc", // Expected FF description
                ),
            )
        }
    }

    private val pollConverter = TransactionConverter(
        useNameForDestination = false,
        enablePrimaryCategorization = false,
        primaryCategoryPrefix = "a",
        enableDetailedCategorization = false,
        detailedCategoryPrefix = "b",
        timeZoneString = "America/New_York",
        transferMatchWindowDays = 10L,
        txStyle = defaultStyle,
    )

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideConvertPollSync")
    fun convertPollSync(
        testName: String,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        plaidCreatedTxs: List<PlaidTransaction>,
        existingFireflyTxs: List<TransactionRead>,
        expectedResult: TransactionConverter.ConvertPollSyncResult,
    ) {
        runBlocking {
            val actual = pollConverter.convertPollSync(
                accountMap,
                plaidCreatedTxs,
                existingFireflyTxs,
            )

            assertEquals(expectedResult, actual)
        }
    }

    @ParameterizedTest(name = "existing Firefly {0}")
    @CsvSource("withdrawal, deposit", "deposit, withdrawal")
    fun `uses the opposite Plaid leg when both legs arrive`(
        originalType: TransactionTypeProperty,
        incomingId: String,
    ) = runBlocking {
        val accountMap = mapOf("checking" to 1, "savings" to 2)
        val plaid = listOf(
            PlaidFixtures.getPaymentTransaction(accountId = "checking", transactionId = "withdrawal", amount = 1111.22),
            PlaidFixtures.getPaymentTransaction(accountId = "savings", transactionId = "deposit", amount = -1111.22),
        )
        val originalId = originalType.name
        val isWithdrawal = originalType == TransactionTypeProperty.withdrawal
        val original = TransactionRead(
            "transactions", "42",
            FireflyFixtures.getTransaction(
                type = originalType,
                sourceId = if (isWithdrawal) "1" else "3",
                destinationId = if (isWithdrawal) "3" else "2",
                currencyCode = "USD",
                externalId = "plaid-$originalId",
            ),
            ObjectLink(),
        )

        val result = pollConverter.convertPollSync(accountMap, plaid, listOf(original))

        val update = result.updates.single()
        val transfer = update.transaction.tx
        assertEquals(emptyList<FireflyTransactionDto>(), result.creates)
        assertEquals(original.id, update.transaction.id)
        assertEquals(incomingId, update.incomingPlaidTransactionId)
        assertEquals(TransactionTypeProperty.transfer, transfer.type)
        assertEquals("1", transfer.sourceId)
        assertEquals("2", transfer.destinationId)
        assertEquals("plaid-$incomingId", transfer.externalId)
        assertEquals("plaid-transfer-leg-$originalId", transfer.internalReference)
    }

    @Test
    fun `batch sync keeps cross currency and tied candidates separate`() = runBlocking {
        val converter = TransactionConverter(
            useNameForDestination = false,
            enablePrimaryCategorization = false,
            primaryCategoryPrefix = "a",
            enableDetailedCategorization = false,
            detailedCategoryPrefix = "b",
            timeZoneString = "America/New_York",
            transferMatchWindowDays = 3L,
            txStyle = defaultStyle,
        )
        val crossCurrencyWithdrawal = PlaidFixtures.getPaymentTransaction(
            accountId = "checking", transactionId = "usd-withdrawal", amount = 100.0,
        )
        val crossCurrencyDeposit = PlaidFixtures.getPaymentTransaction(
            accountId = "savings", transactionId = "eur-deposit", amount = -100.0, isoCurrencyCode = "EUR",
        )
        val withdrawal = crossCurrencyWithdrawal.copy(transactionId = "withdrawal", amount = 200.0)
        val deposit = withdrawal.copy(accountId = "savings", transactionId = "deposit", amount = -200.0)
        val tiedDeposit = deposit.copy(transactionId = "tied-deposit")
        val input = listOf(crossCurrencyWithdrawal, crossCurrencyDeposit, withdrawal, deposit, tiedDeposit)
        val accountMap = mapOf("checking" to 1, "savings" to 2)

        for (transactions in listOf(input, input.reversed())) {
            val result = converter.convertBatchSync(transactions, accountMap)

            assertThat(result.map { it.tx.externalId })
                .containsExactlyInAnyOrderElementsOf(input.map { plaidExternalId(it.transactionId) })
            assertThat(result.map { it.tx.type }).containsExactlyInAnyOrder(
                TransactionTypeProperty.withdrawal,
                TransactionTypeProperty.withdrawal,
                TransactionTypeProperty.deposit,
                TransactionTypeProperty.deposit,
                TransactionTypeProperty.deposit,
            )
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
                txStyle = defaultStyle,
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
            txStyle = defaultStyle,
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
    fun convertProvidesValidExternalUrl(poll: Boolean) {
        val converter = TransactionConverter(
            useNameForDestination = false,
            enablePrimaryCategorization = false,
            primaryCategoryPrefix = "a",
            enableDetailedCategorization = false,
            detailedCategoryPrefix = "b",
            timeZoneString = "America/New_York",
            transferMatchWindowDays = 10L,
            txStyle = defaultStyle,
        )

        val plaidTx = PlaidFixtures.getPaymentTransaction(
            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            name = "Plaid transaction",
            transactionId = "plaidId",
            amount = 123.45,
            website = "example.org",
        )

        val expectedFfTx = FireflyTransactionDto(
            null,
            FireflyFixtures.getTransaction(
                type = TransactionTypeProperty.withdrawal,
                description = "Plaid transaction",
                amount = "123.45",
                sourceId = "1",
                destinationName = "Unknown Transfer Recipient",
                externalId = "plaid-plaidId",
                externalUrl = "https://example.org",
            ).transactions.first()
        )

        val actual = convertCreates(
            converter = converter,
            poll = poll,
            inputPlaidTxs = listOf(plaidTx),
            accountMap = PlaidFixtures.getStandardAccountMapping()
        )

        assertThat(actual).isEqualTo(listOf(expectedFfTx))
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
            txStyle = defaultStyle,
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

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideDescriptionCases")
    fun convertedTxHasExpectedDescription(
        testName: String,
        txStyle: TransactionStyleConfig,
        plaidName: String,
        plaidMerchant: String?,
        plaidOriginalDesc: String?,
        expectedFfDesc: String,
    ) {
        val converter = TransactionConverter(
            useNameForDestination = false,
            enablePrimaryCategorization = false,
            primaryCategoryPrefix = "a",
            enableDetailedCategorization = false,
            detailedCategoryPrefix = "b",
            timeZoneString = "America/New_York",
            transferMatchWindowDays = 10L,
            txStyle = txStyle,
        )

        val plaidTx = PlaidFixtures.getPaymentTransaction(
            accountId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            name = plaidName,
            merchantName = plaidMerchant,
            originalDescription = plaidOriginalDesc,
            transactionId = "plaidId",
            amount = 123.45,
        )

        val expectedFfTx = FireflyTransactionDto(
            null,
            FireflyFixtures.getTransaction(
                type = TransactionTypeProperty.withdrawal,
                description = expectedFfDesc,
                amount = "123.45",
                sourceId = "1",
                destinationName = plaidMerchant ?: "Unknown Transfer Recipient",
                externalId = "plaid-plaidId",
            ).transactions.first()
        )

        val actual = convertCreates(
            converter = converter,
            poll = true,
            inputPlaidTxs = listOf(plaidTx),
            accountMap = PlaidFixtures.getStandardAccountMapping()
        )

        assertThat(actual).isEqualTo(listOf(expectedFfTx))
    }
}
