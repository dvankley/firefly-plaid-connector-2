package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper
import net.djvk.fireflyPlaidConnector2.api.plaid.models.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.time.LocalDate
import java.util.stream.Stream

/**
 * Tests for the PlaidSyncService class.
 */
class PlaidSyncServiceTest {

    companion object {
        private const val PLAID_BATCH_SIZE = 100
        private const val ALLOW_ITEM_TO_FAIL = true

        @JvmStatic
        fun provideGetTransactionSyncRequestTestCases(): Stream<Arguments> {
            return Stream.of(
                // Test case 1: With cursor
                Arguments.of(
                    "With cursor",
                    "token1",
                    "cursor1",
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token1",
                        cursor = "cursor1",
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),
                
                // Test case 2: Without cursor
                Arguments.of(
                    "Without cursor",
                    "token2",
                    null,
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token2",
                        cursor = null,
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),
                
                // Test case 3: Custom batch size
                Arguments.of(
                    "Custom batch size",
                    "token3",
                    "cursor3",
                    50, // Custom batch size
                    TransactionsSyncRequest(
                        accessToken = "token3",
                        cursor = "cursor3",
                        count = 50, // Custom batch size
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),
                
                // Test case 4: Empty cursor
                Arguments.of(
                    "Empty cursor",
                    "token4",
                    "",
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token4",
                        cursor = "",
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),
                
                // Test case 5: Very large batch size
                Arguments.of(
                    "Very large batch size",
                    "token5",
                    "cursor5",
                    1000, // Very large batch size
                    TransactionsSyncRequest(
                        accessToken = "token5",
                        cursor = "cursor5",
                        count = 1000, // Very large batch size
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),
                
                // Test case 6: Very small batch size
                Arguments.of(
                    "Very small batch size",
                    "token6",
                    "cursor6",
                    1, // Very small batch size
                    TransactionsSyncRequest(
                        accessToken = "token6",
                        cursor = "cursor6",
                        count = 1, // Very small batch size
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),
                
                // Test case 7: Long token and cursor
                Arguments.of(
                    "Long token and cursor",
                    "token7".repeat(10), // Long token
                    "cursor7".repeat(10), // Long cursor
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token7".repeat(10), // Long token
                        cursor = "cursor7".repeat(10), // Long cursor
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                ),
                
                // Test case 8: Special characters in token and cursor
                Arguments.of(
                    "Special characters in token and cursor",
                    "token8!@#$%^&*()",
                    "cursor8!@#$%^&*()",
                    PLAID_BATCH_SIZE,
                    TransactionsSyncRequest(
                        accessToken = "token8!@#$%^&*()",
                        cursor = "cursor8!@#$%^&*()",
                        count = PLAID_BATCH_SIZE,
                        options = TransactionsSyncRequestOptions(
                            includeOriginalDescription = true,
                            includePersonalFinanceCategory = true
                        )
                    )
                )
            )
        }
    }

    private val plaidApiWrapper: PlaidApiWrapper = mock()
    private val plaidSyncService = PlaidSyncService(
        plaidApiWrapper,
        PLAID_BATCH_SIZE,
        ALLOW_ITEM_TO_FAIL
    )

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("provideGetTransactionSyncRequestTestCases")
    fun testGetTransactionSyncRequest(
        testName: String,
        accessToken: String,
        cursor: String?,
        batchSize: Int,
        expectedRequest: TransactionsSyncRequest
    ) {
        // Execute
        val request = plaidSyncService.getTransactionSyncRequest(accessToken, cursor, batchSize)
        
        // Verify
        assertEquals(expectedRequest.accessToken, request.accessToken)
        assertEquals(expectedRequest.cursor, request.cursor)
        assertEquals(expectedRequest.count, request.count)
        assertEquals(expectedRequest.options?.includeOriginalDescription, request.options?.includeOriginalDescription)
        assertEquals(expectedRequest.options?.includePersonalFinanceCategory, request.options?.includePersonalFinanceCategory)
    }
}