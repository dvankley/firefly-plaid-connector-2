package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Account
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AccountRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AccountRoleProperty
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AccountSingle
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ShortAccountTypeProperty
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.AccountBalance
import net.djvk.fireflyPlaidConnector2.api.plaid.models.AccountBase
import net.djvk.fireflyPlaidConnector2.api.plaid.models.AccountType
import net.djvk.fireflyPlaidConnector2.api.plaid.models.AccountsBalanceGetRequest
import net.djvk.fireflyPlaidConnector2.api.plaid.models.AccountsGetResponse
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.lib.FireflyMock
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidMock
import net.djvk.fireflyPlaidConnector2.lib.createFireflyResponse
import net.djvk.fireflyPlaidConnector2.lib.createPlaidResponse
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class BatchSyncRunnerInitialBalanceTest {
    private val token = "item-token"
    private val plaidAccount = "plaidAccount1"
    private val fireflyAccount = 1
    private val plaid = PlaidMock()
    private val firefly = FireflyMock()
    private val syncHelper = mock<SyncHelper> {
        on { getAllPlaidAccessTokenAccountIdSets() } doReturn Pair(
            mapOf(plaidAccount to fireflyAccount),
            sequenceOf(token to listOf(plaidAccount)),
        )
    }
    private val runner = BatchSyncRunnerTest.createRunner(plaid, firefly, setInitialBalance = true, syncHelper = syncHelper)

    // Plaid amounts are positive for money leaving the account, so these net to 60 out.
    private val txs = listOf(
        tx("out", 100.0, LocalDate.of(2025, 1, 5)),
        tx("in", -40.0, LocalDate.of(2025, 1, 3)),
    )

    // Midnight on the earliest transaction's date in America/New_York, minus the one hour offset.
    private val expectedDate = OffsetDateTime.of(2025, 1, 2, 23, 0, 0, 0, ZoneOffset.ofHours(-5))

    @Test
    fun `asset account balance adds back the window's net outflow as an opening deposit`() = runBlocking {
        stubBalance(current = 500.0)
        stubFireflyAccount(AccountRoleProperty.defaultAsset)

        runner.setInitialBalances(mapOf(token to txs), syncHelper, LocalDate.of(2025, 1, 1))

        val split = insertedSplit()
        assertThat(split.type).isEqualTo(TransactionTypeProperty.deposit)
        assertThat(split.amount).isEqualTo("560.0")
        assertThat(split.destinationId).isEqualTo(fireflyAccount.toString())
        assertThat(split.sourceId).isNull()
        assertThat(split.sourceName).isEqualTo("Initial Balance")
        assertThat(split.date.isEqual(expectedDate)).isTrue()
    }

    @Test
    fun `credit card balance is treated as owed, producing an opening withdrawal`() = runBlocking {
        stubBalance(current = 500.0)
        stubFireflyAccount(AccountRoleProperty.ccAsset)

        runner.setInitialBalances(mapOf(token to txs), syncHelper, LocalDate.of(2025, 1, 1))

        val split = insertedSplit()
        assertThat(split.type).isEqualTo(TransactionTypeProperty.withdrawal)
        assertThat(split.amount).isEqualTo("440.0")
        assertThat(split.sourceId).isEqualTo(fireflyAccount.toString())
        assertThat(split.destinationId).isNull()
    }

    @Test
    fun `account without a current balance is skipped`() = runBlocking {
        stubBalance(current = null)
        stubFireflyAccount(AccountRoleProperty.defaultAsset)

        runner.setInitialBalances(mapOf(token to txs), syncHelper, LocalDate.of(2025, 1, 1))

        verify(syncHelper, never()).optimisticInsertBatchIntoFirefly(any())
    }

    @Test
    fun `balance accounts not mapped to Firefly are skipped`() = runBlocking {
        stubBalance(current = 500.0, accountId = "unmapped")

        runner.setInitialBalances(mapOf(token to txs), syncHelper, LocalDate.of(2025, 1, 1))

        verify(syncHelper, never()).optimisticInsertBatchIntoFirefly(any())
        verify(firefly.accountsApi, never()).getAccount(any(), anyOrNull())
    }

    @Test
    fun `a failed balance request skips the item instead of aborting`() = runBlocking {
        whenever(plaid.api.accountsBalanceGet(any())).thenThrow(RuntimeException("balance unavailable"))

        runner.setInitialBalances(mapOf(token to txs), syncHelper, LocalDate.of(2025, 1, 1))

        verify(syncHelper, never()).optimisticInsertBatchIntoFirefly(any())
    }

    @Test
    fun `items with no fetched transactions do not request balances`() = runBlocking {
        runner.setInitialBalances(emptyMap(), syncHelper, LocalDate.of(2025, 1, 1))

        verify(plaid.api, never()).accountsBalanceGet(any())
        verify(syncHelper, never()).optimisticInsertBatchIntoFirefly(any())
    }

    @Test
    fun `balance request asks only for the item's configured accounts`() = runBlocking {
        stubBalance(current = 500.0)
        stubFireflyAccount(AccountRoleProperty.defaultAsset)

        runner.setInitialBalances(mapOf(token to txs), syncHelper, LocalDate.of(2025, 1, 1))

        val request = argumentCaptor<AccountsBalanceGetRequest>()
        verify(plaid.api).accountsBalanceGet(request.capture())
        assertThat(request.firstValue.accessToken).isEqualTo(token)
        assertThat(request.firstValue.options?.accountIds).containsExactly(plaidAccount)
        assertThat(request.firstValue.options?.minLastUpdatedDatetime).isNull()
    }

    private fun tx(id: String, amount: Double, date: LocalDate): Transaction =
        PlaidFixtures.getPaymentTransaction(transactionId = id, accountId = plaidAccount, amount = amount, date = date)

    private suspend fun stubBalance(current: Double?, accountId: String = plaidAccount) {
        val account = AccountBase(
            accountId = accountId,
            balances = AccountBalance(available = null, current = current, limit = null, isoCurrencyCode = "USD", unofficialCurrencyCode = null),
            mask = null,
            name = "Checking",
            officialName = null,
            type = AccountType.depository,
            subtype = null,
        )
        // Build responses before stubbing: they touch a shared mock, which Mockito rejects mid-stubbing.
        val response = createPlaidResponse(AccountsGetResponse(listOf(account), PlaidFixtures.getItem(), "request"))
        whenever(plaid.api.accountsBalanceGet(any())).thenReturn(response)
    }

    private suspend fun stubFireflyAccount(role: AccountRoleProperty) {
        val account = AccountRead(
            "accounts", fireflyAccount.toString(),
            Account(name = "Checking", type = ShortAccountTypeProperty.asset, accountRole = role),
            ObjectLink(),
        )
        val response = createFireflyResponse(AccountSingle(account))
        whenever(firefly.accountsApi.getAccount(eq(fireflyAccount.toString()), anyOrNull())).thenReturn(response)
    }

    private suspend fun insertedSplit(): net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit {
        val captor = argumentCaptor<List<FireflyTransactionDto>>()
        verify(syncHelper).optimisticInsertBatchIntoFirefly(captor.capture())
        return captor.firstValue.single().tx
    }
}
