package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper

/**
 * Tests for the PlaidSyncService class.
 */
class PlaidSyncServiceTest {

    private val plaidApiWrapper: PlaidApiWrapper = mock()
    private val plaidBatchSize = 100
    private val allowItemToFail = true

    private val plaidSyncService = PlaidSyncService(
        plaidApiWrapper,
        plaidBatchSize,
        allowItemToFail
    )

    @Test
    fun testInitializeCursorsSkipsTokensWithExistingCursors() {
        runBlocking {
            // Setup
            val accessToken = "test_access_token"
            val accountIds = listOf("account1")
            val cursorMap = mutableMapOf(accessToken to "existing_cursor")
            val accountAccessTokenSequence = sequenceOf(Pair(accessToken, accountIds))

            // Execute
            plaidSyncService.initializeCursors(accountAccessTokenSequence, cursorMap)

            // Verify - API should not be called for tokens with existing cursors
            assertEquals("existing_cursor", cursorMap[accessToken])

            // Use a simpler verification that doesn't require type inference
            Mockito.verifyNoInteractions(plaidApiWrapper)
        }
    }
}
