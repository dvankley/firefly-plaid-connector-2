package net.djvk.fireflyPlaidConnector2.sync

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.ApiConfiguration
import net.djvk.fireflyPlaidConnector2.api.RetryProperties
import net.djvk.fireflyPlaidConnector2.api.firefly.FireflyApiWrapper
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.SearchApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.ApiClient
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Meta
import net.djvk.fireflyPlaidConnector2.api.firefly.models.ObjectLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.PageLink
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Transaction
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionArray
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction
import net.djvk.fireflyPlaidConnector2.config.properties.AccountConfigs
import net.djvk.fireflyPlaidConnector2.lib.FireflyFixtures
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TransferReplacementRecoveryTest {
    @TempDir
    lateinit var directory: Path

    private val originalDate = OffsetDateTime.parse("2022-09-01T04:00:00Z")
    private val original = fireflyRead("1", FireflyFixtures.getTransaction(
        date = originalDate, processDate = originalDate, sourceId = "10", externalId = "plaid-old",
    ))
    private val transfer = fireflyRead("2", FireflyFixtures.getTransaction(
        type = TransactionTypeProperty.transfer,
        date = OffsetDateTime.parse("2022-10-03T10:30:00-04:00"),
        amount = "1111.2200",
        sourceId = "10",
        destinationId = "20",
        externalId = "plaid-new",
    ))
    private val update = FireflyTransactionDto(original.id, transfer.attributes.transactions.single().copy(amount = "1111.22"))
    private val intent = TransferReplacement(original.id, original.attributes.transactions.single(), update.toTransactionStore())
    private val oldCursors = mapOf("token" to "old")
    private val nextCursors = mapOf("token" to "next")
    private val journalPath get() = directory.resolve("pending_transfer_replacements.json")

    @Test
    fun deleteFailureLeavesOriginalAndCursorUntilRestart() = runBlocking {
        val firefly = FireflyServer().apply { rejectDelete = true }
        val cursor = seededCursor()
        val journal = TransferReplacementJournal(cursor)
        val cursors = cursor.readCursorMap()

        assertEquals(HttpStatusCode.Forbidden, assertFailsWith<ClientRequestException> {
            runPoll(firefly, cursor, cursors)
        }.response.status)
        assertEquals(oldCursors, cursors)
        assertEquals(oldCursors, cursor.readCursorMap())
        assertEquals(listOf(intent), journal.pendingFor(cursors))
        assertTrue(firefly.originalExists)
        assertEquals(0, firefly.posts)

        firefly.rejectDelete = false
        runPoll(firefly)

        assertCommitted(firefly, expectedPosts = 1)
    }

    @Test
    fun createFailureAfterDeleteLeavesPayloadAndCursorUntilRestart() = runBlocking {
        val firefly = FireflyServer().apply { rejectCreate = true }
        val cursor = seededCursor()
        val cursors = cursor.readCursorMap()

        assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ClientRequestException> {
            runPoll(firefly, cursor, cursors)
        }.response.status)
        assertEquals(oldCursors, cursors)
        assertEquals(oldCursors, cursor.readCursorMap())
        assertEquals(listOf(intent), TransferReplacementJournal(cursor).pendingFor(cursors))
        assertFalse(firefly.originalExists)
        assertFalse(firefly.transferExists)

        firefly.rejectCreate = false
        runPoll(firefly)

        assertCommitted(firefly, expectedPosts = 2)
    }

    @Test
    fun restartAfterDeleteBeforePostUsesSavedPayload() = runBlocking {
        val cursor = seededCursor()
        TransferReplacementJournal(cursor).append(listOf(intent))
        val firefly = FireflyServer().apply { originalExists = false }

        runPoll(firefly)

        assertCommitted(firefly, expectedPosts = 1)
    }

    @Test
    fun lostSuccessfulCreateResponseIsReconciledWithoutAnotherPost() = runBlocking {
        val firefly = FireflyServer().apply { loseCreateResponse = true }
        val cursor = seededCursor()
        val cursors = cursor.readCursorMap()

        assertFailsWith<IOException> { runPoll(firefly, cursor, cursors) }
        assertEquals(oldCursors, cursors)
        assertEquals(oldCursors, cursor.readCursorMap())
        assertEquals(listOf(intent), TransferReplacementJournal(cursor).pendingFor(cursors))
        assertTrue(firefly.transferExists)
        assertEquals(1, firefly.posts)

        runPoll(firefly)

        assertCommitted(firefly, expectedPosts = 1)
    }

    @Test
    fun restartAfterCursorCommitDiscardsJournalWithoutReconciling() = runBlocking {
        val cursor = seededCursor()
        val journal = TransferReplacementJournal(cursor)
        journal.append(listOf(intent))
        journal.complete(original.id)
        journal.prepareCursorCommit(nextCursors)
        cursor.writeCursorMap(nextCursors)
        assertTrue(Files.exists(journalPath))
        val firefly = FireflyServer().apply {
            originalExists = false
            transferExists = true
        }

        runPoll(firefly)

        assertCommitted(firefly, expectedPosts = 0)
        assertEquals(0, firefly.searches)
        assertEquals(0, firefly.deletes)
    }

    private suspend fun seededCursor() = CursorManager(directory.toString()).also { it.writeCursorMap(oldCursors) }

    private suspend fun assertCommitted(firefly: FireflyServer, expectedPosts: Int) {
        assertEquals(nextCursors, CursorManager(directory.toString()).readCursorMap())
        assertFalse(Files.exists(journalPath))
        assertFalse(firefly.originalExists)
        assertTrue(firefly.transferExists)
        assertEquals(expectedPosts, firefly.posts)
    }

    private suspend fun runPoll(
        firefly: FireflyServer,
        cursor: CursorManager = CursorManager(directory.toString()),
        cursors: MutableMap<PlaidAccessToken, PlaidSyncCursor>? = null,
    ) {
        val journal = TransferReplacementJournal(cursor)
        val client = ApiConfiguration().getFireflyClientConfig(60_000)
        val transactions = TransactionsApi("https://firefly.test", firefly.engine, client)
        val wrapper = FireflyApiWrapper(RetryProperties(maxAttempts = 3, initialDelayMs = 0))
        val helper = SyncHelper(
            AccountConfigs(emptyList()), "testToken", mock(), transactions, mock(), wrapper,
            SearchApi("https://firefly.test", firefly.engine, client),
        )
        val service = FireflyTransactionService(transactions, helper, wrapper, journal, 30)
        val plaid: PlaidSyncService = mock()
        val converter: TransactionConverter = mock()
        val added = listOf("old", "new").map { id ->
            mock<PlaidTransaction> { on { transactionId }.thenReturn(id) }
        }
        val cursorMap = cursors ?: cursor.readCursorMap()
        val alreadyCommitted = cursorMap == nextCursors
        val restoring = Files.exists(journalPath) && !alreadyCommitted
        whenever(plaid.processPlaidTransactions(any(), any(), any())).thenAnswer {
            assertEquals(restoring, it.getArgument<Boolean>(2))
            it.getArgument<MutableMap<PlaidAccessToken, PlaidSyncCursor>>(1).putAll(nextCursors)
            PlaidTransactionResult(if (alreadyCommitted) emptyList() else added, emptyList(), emptyList())
        }
        whenever(converter.convertPollSync(any(), any(), any())).thenAnswer {
            assertEquals(if (alreadyCommitted || restoring) emptyList() else added, it.getArgument(1))
            TransactionConverter.ConvertPollSyncResult(
                emptyList(), if (alreadyCommitted || restoring) emptyList() else listOf(update),
            )
        }
        PolledSyncOrchestrator(30, helper, cursor, plaid, service, converter, journal, PlaidCorrectionQueue(cursor, helper, converter))
            .processTransactions(emptyMap(), emptySequence(), cursorMap)
    }

    private inner class FireflyServer {
        var originalExists = true
        var transferExists = false
        var rejectDelete = false
        var rejectCreate = false
        var loseCreateResponse = false
        var posts = 0
        var deletes = 0
        var searches = 0
        private val mapper = jacksonObjectMapper().apply(ApiClient.JSON_DEFAULT)
        private val headers = headersOf(HttpHeaders.ContentType, "application/json")

        val engine = MockEngine { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v1/transactions" -> respond(
                    transactionsJson(listOfNotNull(original.takeIf { originalExists })), headers = headers,
                )
                HttpMethod.Get to "/api/v1/search/transactions" -> {
                    searches++
                    assertEquals("external_id_is:plaid-new", request.url.parameters["query"])
                    respond(transactionsJson(listOfNotNull(transfer.takeIf { transferExists })), headers = headers)
                }
                HttpMethod.Delete to "/api/v1/transactions/1" -> {
                    deletes++
                    assertTrue(Files.exists(journalPath))
                    when {
                        rejectDelete -> respond("{}", HttpStatusCode.Forbidden, headers)
                        !originalExists -> respond("{}", HttpStatusCode.NotFound, headers)
                        else -> {
                            originalExists = false
                            respond("", HttpStatusCode.NoContent)
                        }
                    }
                }
                HttpMethod.Post to "/api/v1/transactions" -> {
                    posts++
                    assertFalse(originalExists)
                    assertFalse(transferExists)
                    if (rejectCreate) {
                        respond("{}", HttpStatusCode.BadRequest, headers)
                    } else {
                        transferExists = true
                        if (loseCreateResponse) throw IOException("Response lost after Firefly committed")
                        respond("{}", HttpStatusCode.Created, headers)
                    }
                }
                else -> error("Unexpected Firefly request ${request.method} ${request.url}")
            }
        }

        private fun transactionsJson(transactions: List<TransactionRead>): String =
            mapper.writeValueAsString(TransactionArray(transactions, Meta(), PageLink()))
    }

    private fun fireflyRead(id: String, transaction: Transaction) = TransactionRead(
        type = "transactions",
        id = id,
        attributes = transaction,
        links = ObjectLink(self = URI("https://firefly.test/api/v1/transactions/$id")),
    )
}
