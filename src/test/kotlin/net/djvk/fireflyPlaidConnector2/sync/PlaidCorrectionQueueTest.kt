package net.djvk.fireflyPlaidConnector2.sync

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.plaid.models.RemovedTransaction
import net.djvk.fireflyPlaidConnector2.config.properties.TransactionStyleConfig
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

internal class PlaidCorrectionQueueTest {
    @TempDir
    lateinit var directory: Path

    private val helper: SyncHelper = mock()
    private val accountMap = mapOf("account" to 1)
    private val before = mapOf("test-token" to "before")
    private val after = mapOf("test-token" to "after")
    private val now = Instant.parse("2026-11-22T12:00:00Z")
    private val empty = PlaidTransactionResult(emptyList(), emptyList(), emptyList())
    private val converter = TransactionConverter(
        true, "America/New_York", 3, true, "plaid-", false, "", TransactionStyleConfig(null)
    )

    private fun cursor() = CursorManager(directory.toString())
    private fun queue() = PlaidCorrectionQueue(cursor(), helper, converter)
    private fun queuePath() = directory.resolve("plaid_unresolved_changes.json")
    private fun entries() = jacksonObjectMapper().readTree(queuePath().toFile()).required("entries")

    @Test
    fun `an old modification and removal succeed while a bad id survives restart and reaches review`() = runBlocking {
        val old = PlaidFixtures.getPaymentTransaction(accountId = "account", transactionId = "old-update", amount = 15.0)
        val bad = old.copy(transactionId = "unknown")
        val removed = RemovedTransaction("old-delete", "account")
        whenever(helper.findTransactionsByExternalId("plaid-old-update"))
            .thenReturn(listOf(target("21", "old-update")))
        whenever(helper.findTransactionsByExternalId("plaid-old-delete"))
            .thenReturn(listOf(target("22", "old-delete")))
        whenever(helper.findTransactionsByExternalId("plaid-unknown")).thenReturn(emptyList())

        poll(before, after, empty.copy(updated = listOf(old, bad), deleted = listOf(removed)))

        verify(helper).updateBatchInFirefly(argThat { single().id == "21" && single().tx.amount == "15.0" })
        verify(helper).deleteBatchInFirefly(listOf("22"))
        assertEquals(after, cursor().readCursorMap())
        assertEquals("pending", entries().single().required("status").asText())

        for (minutes in listOf(20L, 40L, 60L)) {
            queue().process(accountMap, empty, after, after, Instant.now().plusSeconds(minutes * 60))
        }
        assertEquals("needs_review", entries().single().required("status").asText())
        assertEquals("no_match", entries().single().required("lastFailure").asText())
        assertEquals(4, entries().single().required("attempts").asInt())

        val next = mapOf("test-token" to "continued")
        poll(after, next, empty.copy(updated = listOf(old.copy(amount = 30.0))))
        assertEquals(next, cursor().readCursorMap())
        verify(helper).updateBatchInFirefly(argThat { single().id == "21" && single().tx.amount == "30.0" })
        verify(helper, times(4)).findTransactionsByExternalId("plaid-unknown")
    }

    @Test
    fun `a queued lookup failure replays the full modification after restart`() = runBlocking {
        val fixture = PlaidFixtures.getPaymentTransaction(accountId = "account", transactionId = "late")
        val update = fixture.copy(location = fixture.location.copy(lat = 40.25, lon = -75.25))
        whenever(helper.findTransactionsByExternalId("plaid-late")).thenAnswer { throw IOException("offline") }
        queue().process(accountMap, empty.copy(updated = listOf(update)), before, after, now)
        cursor().writeCursorMap(after)
        assertEquals("lookup_failed", entries().single().required("lastFailure").asText())
        val savedTransaction = entries().single().required("transaction")
        assertEquals(40.25, savedTransaction.required("location").required("lat").asDouble())
        assertEquals("TRANSFER_OUT", savedTransaction.required("personal_finance_category").required("primary").asText())

        doReturn(listOf(target("24", "late"))).whenever(helper).findTransactionsByExternalId("plaid-late")
        queue().process(accountMap, empty, after, after, now.plusSeconds(60))
        queue().finishCursorCommit(after)
        verify(helper).updateBatchInFirefly(argThat {
            single().id == "24" && single().tx.latitude == 40.25 && single().tx.tags == listOf("plaid-transfer-out")
        })
        assertFalse(Files.exists(queuePath()))
    }

    @Test
    fun `a later removal supersedes a queued update and is not repeated on cursor replay`() = runBlocking {
        val update = PlaidFixtures.getPaymentTransaction(accountId = "account", transactionId = "superseded")
        val removal = empty.copy(deleted = listOf(RemovedTransaction("superseded", "account")))
        val next = mapOf("test-token" to "next")
        whenever(helper.findTransactionsByExternalId("plaid-superseded"))
            .thenReturn(emptyList(), listOf(target("25", "superseded")))

        queue().process(accountMap, empty.copy(updated = listOf(update)), before, after, now)
        cursor().writeCursorMap(after)
        queue().process(accountMap, removal, after, next, now.plusSeconds(60))
        queue().process(accountMap, removal, after, next, now.plusSeconds(61))
        cursor().writeCursorMap(next)
        queue().finishCursorCommit(next)

        verify(helper, times(2)).findTransactionsByExternalId("plaid-superseded")
        verify(helper, times(0)).updateBatchInFirefly(any())
        verify(helper).deleteBatchInFirefly(listOf("25"))
        assertFalse(Files.exists(queuePath()))
    }

    @Test
    fun `a removal whose response was lost is resolved when the verified target is absent on retry`() = runBlocking {
        val removal = empty.copy(deleted = listOf(RemovedTransaction("lost-response", "account")))
        whenever(helper.findTransactionsByExternalId("plaid-lost-response"))
            .thenReturn(listOf(target("26", "lost-response")), emptyList())
        whenever(helper.deleteBatchInFirefly(listOf("26"))).thenAnswer { throw IOException("lost response") }

        queue().process(accountMap, removal, before, after, now)
        cursor().writeCursorMap(after)
        assertEquals("write_failed", entries().single().required("lastFailure").asText())

        queue().process(accountMap, empty, after, after, now.plusSeconds(60))
        queue().finishCursorCommit(after)
        verify(helper).deleteBatchInFirefly(listOf("26"))
        assertFalse(Files.exists(queuePath()))
    }

    @Test
    fun `a queue storage failure leaves the on-disk cursor intact`() = runBlocking {
        cursor().writeCursorMap(before)
        Files.createDirectory(queuePath())

        assertFailsWith<IOException> { poll(before, after, empty) }

        assertEquals(before, cursor().readCursorMap())
    }

    private suspend fun poll(
        current: Map<PlaidAccessToken, PlaidSyncCursor>,
        next: Map<PlaidAccessToken, PlaidSyncCursor>,
        changes: PlaidTransactionResult,
    ) {
        val cursor = cursor()
        if (cursor.readCursorMap().isEmpty()) cursor.writeCursorMap(current)
        val plaid: PlaidSyncService = mock()
        val firefly: FireflyTransactionService = mock()
        whenever(firefly.fetchExistingFireflyTransactions()).thenReturn(emptyList())
        whenever(plaid.processPlaidTransactions(any(), any(), any())).thenAnswer {
            it.getArgument<MutableMap<PlaidAccessToken, PlaidSyncCursor>>(1).putAll(next)
            changes
        }
        PolledSyncOrchestrator(10, helper, cursor, plaid, firefly, converter, TransferReplacementJournal(cursor), queue())
            .processTransactions(accountMap, emptySequence(), cursor.readCursorMap())
    }

    private fun target(id: String, plaidId: String) = TransactionRead(
        "transactions", id, FireflyFixtures.getTransaction(sourceId = "1", externalId = "plaid-$plaidId"), ObjectLink()
    )
}
