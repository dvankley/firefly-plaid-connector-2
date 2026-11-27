package net.djvk.fireflyPlaidConnector2.sync

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.RemovedTransaction
import net.djvk.fireflyPlaidConnector2.config.properties.TransactionStyleConfig
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.lib.testTransactionConverter
import net.djvk.fireflyPlaidConnector2.lib.PlaidFixtures
import net.djvk.fireflyPlaidConnector2.lib.testPolledProperties
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    private val converter = testTransactionConverter(
        true, "America/New_York", 3, true, "plaid-", false, "", TransactionStyleConfig(null)
    )

    private fun cursor() = CursorManager(directory.toString())
    private fun queue() = PlaidCorrectionQueue(cursor(), helper, converter)
    private fun queuePath() = directory.resolve("plaid_unresolved_changes.json")
    private fun entries() = jacksonObjectMapper().readTree(queuePath().toFile()).required("entries")

    @BeforeEach
    fun stubInternalReferenceSearch(): Unit = runBlocking {
        whenever(helper.findTransactionsByInternalReference(any())).thenReturn(emptyList())
    }

    @Test
    fun `an old modification and removal succeed while a bad id survives restart and reaches review`(): Unit = runBlocking {
        val old = PlaidFixtures.getPaymentTransaction(accountId = "account", transactionId = "old-update", amount = 15.0)
        val bad = old.copy(transactionId = "unknown")
        val removed = RemovedTransaction("old-delete", "account")
        whenever(helper.findTransactionsByExternalId("plaid-old-update"))
            .thenReturn(listOf(target("21", "old-update")))
        whenever(helper.getTransactionGroup("21")).thenReturn(target("21", "old-update"))
        whenever(helper.findTransactionsByExternalId("plaid-old-delete"))
            .thenReturn(listOf(target("22", "old-delete")))
        whenever(helper.getTransactionGroup("22")).thenReturn(target("22", "old-delete"))
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
    fun `removing one split retains the group and matching journal for review`() = runBlocking {
        val group = target("27", "split-to-remove")
        val split = group.attributes.transactions.single().copy(transactionJournalId = "71")
        val otherSplit = split.copy(transactionJournalId = "72", externalId = "plaid-other-split")
        whenever(helper.findTransactionsByExternalId("plaid-split-to-remove"))
            .thenReturn(listOf(group.copy(attributes = group.attributes.copy(transactions = listOf(split)))))
        whenever(helper.getTransactionGroup("27"))
            .thenReturn(group.copy(attributes = group.attributes.copy(transactions = listOf(split, otherSplit))))

        val removal = RemovedTransaction("split-to-remove", "account")
        poll(before, after, empty.copy(deleted = listOf(removal)))

        assertReviewTarget("split-to-remove", "27", "71")
        assertEquals(after, cursor().readCursorMap())
        verify(helper, never()).deleteBatchInFirefly(any())
        verify(helper, never()).updateBatchInFirefly(any())
    }

    @Test
    fun `a search hit whose external ID changed goes to review without deleting the group`() = runBlocking {
        whenever(helper.findTransactionsByExternalId("plaid-original"))
            .thenReturn(listOf(target("30", "original")))
        whenever(helper.getTransactionGroup("30")).thenReturn(target("30", "different"))

        val removal = RemovedTransaction("original", "account")
        poll(before, after, empty.copy(deleted = listOf(removal)))

        val entry = entries().single()
        assertEquals("needs_review", entry.required("status").asText())
        assertEquals("target_changed", entry.required("lastFailure").asText())
        verify(helper, never()).deleteBatchInFirefly(any())
        verify(helper, never()).updateBatchInFirefly(any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `either leg of a tracked transfer goes directly to review`(removePrimary: Boolean) = runBlocking {
        val target = trackedTransfer()
        whenever(helper.findTransactionsByExternalId("plaid-primary")).thenReturn(listOf(target))
        whenever(helper.findTransactionsByExternalId("plaid-secondary")).thenReturn(emptyList())
        whenever(helper.findTransactionsByInternalReference("plaid-transfer-leg-secondary")).thenReturn(listOf(target))
        whenever(helper.getTransactionGroup(target.id)).thenReturn(target)
        val (removedId, updatedId) = if (removePrimary) "primary" to "secondary" else "secondary" to "primary"
        val update = PlaidFixtures.getPaymentTransaction(accountId = "account", transactionId = updatedId)
        val removal = RemovedTransaction(removedId, "account")

        poll(before, after, empty.copy(updated = listOf(update), deleted = listOf(removal)))

        assertReviewTarget(removedId, target.id, "73")
        assertReviewTarget(updatedId, target.id, "73")
        assertEquals(after, cursor().readCursorMap())
        verify(helper, never()).deleteBatchInFirefly(any())
        verify(helper, never()).updateBatchInFirefly(any())
    }

    @Test
    fun `a transfer leg with its own Firefly entry is ambiguous`() = runBlocking {
        whenever(helper.findTransactionsByExternalId("plaid-secondary")).thenReturn(listOf(target("31", "secondary")))
        whenever(helper.findTransactionsByInternalReference("plaid-transfer-leg-secondary")).thenReturn(listOf(trackedTransfer()))

        poll(before, after, empty.copy(deleted = listOf(RemovedTransaction("secondary", "account"))))

        val entry = entries().single()
        assertEquals("needs_review", entry.required("status").asText())
        assertEquals("ambiguous_match", entry.required("lastFailure").asText())
        assertEquals(setOf("28", "31"), entry.required("reviewTargets").map { it.required("groupId").asText() }.toSet())
        verify(helper, never()).deleteBatchInFirefly(any())
    }

    private fun trackedTransfer() = TransactionRead(
        "transactions", "28", FireflyFixtures.getTransaction(
            type = TransactionTypeProperty.transfer, sourceId = "1", destinationId = "2", transactionJournalId = "73",
            externalId = "plaid-primary", internalReference = "plaid-transfer-leg-secondary",
        ), ObjectLink(),
    )

    private fun assertReviewTarget(transactionId: String, groupId: String, journalId: String) {
        val entry = entries().single { it.required("transactionId").asText() == transactionId }
        assertEquals("needs_review", entry.required("status").asText())
        assertEquals("unsupported_target", entry.required("lastFailure").asText())
        assertEquals(1, entry.required("attempts").asInt())
        val target = entry.required("reviewTargets").single()
        assertEquals(groupId, target.required("groupId").asText())
        assertEquals(journalId, target.required("journalId").asText())
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
        whenever(helper.getTransactionGroup("24")).thenReturn(target("24", "late"))
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
        whenever(helper.getTransactionGroup("25")).thenReturn(target("25", "superseded"))

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
        whenever(helper.getTransactionGroup("26")).thenReturn(target("26", "lost-response"))
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
        PolledSyncOrchestrator(
            testPolledProperties(syncFrequencyMinutes = 10),
            helper, cursor, plaid, firefly, converter, TransferReplacementJournal(cursor), queue(),
        )
            .processTransactions(accountMap, emptySequence(), cursor.readCursorMap())
    }

    private fun target(id: String, plaidId: String) = TransactionRead(
        "transactions", id, FireflyFixtures.getTransaction(sourceId = "1", externalId = "plaid-$plaidId"), ObjectLink()
    )
}
