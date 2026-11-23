package net.djvk.fireflyPlaidConnector2.sync

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionSplitId
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Location
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PaymentMeta
import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategory
import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction
import net.djvk.fireflyPlaidConnector2.api.plaid.models.TransactionCounterparty
import net.djvk.fireflyPlaidConnector2.transactions.FireflyAccountId
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import net.djvk.fireflyPlaidConnector2.transactions.isPlaidTransferReference
import net.djvk.fireflyPlaidConnector2.transactions.plaidExternalId
import net.djvk.fireflyPlaidConnector2.transactions.plaidTransferReference
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val MAX_ATTEMPTS = 4
private const val MAX_RETRIES_PER_POLL = 10
private const val REQUEST_TIMEOUT_MS = 5_000L

class PlaidCorrectionQueue(
    cursorManager: CursorManager,
    private val syncHelper: SyncHelper,
    private val converter: TransactionConverter,
) {
    private val path = cursorManager.cursorFilePath.resolveSibling("plaid_unresolved_changes.json")
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(WRITE_DATES_AS_TIMESTAMPS)
        .disable(ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .apply {
            // Some generated Plaid models extend HashMap. Serialize their typed properties as well.
            for (model in listOf(Location::class.java, PaymentMeta::class.java,
                PersonalFinanceCategory::class.java, TransactionCounterparty::class.java)) {
                if (Map::class.java.isAssignableFrom(model)) addMixIn(model, PlaidObject::class.java)
            }
        }

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    private interface PlaidObject

    private enum class Change { modified, removed }
    private enum class Status { pending, needs_review, applied }

    private data class ReviewTarget(val groupId: FireflyTransactionId, val journalId: FireflyTransactionSplitId?)

    private data class Entry(
        val accountId: PlaidAccountId,
        val transactionId: String,
        val change: Change,
        val transaction: Transaction? = null,
        val firstSeen: Instant,
        val status: Status = Status.pending,
        val attempts: Int = 0,
        val nextRetry: Instant? = firstSeen,
        val lastFailure: String? = null,
        val removalMatched: Boolean = false,
        val reviewTargets: List<ReviewTarget> = emptyList(),
    ) {
        fun key() = accountId to transactionId
    }

    private data class State(
        val entries: List<Entry> = emptyList(),
        val commitFingerprint: String? = null,
        val version: Int = 1,
    )

    internal suspend fun process(
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        changes: PlaidTransactionResult,
        cursors: Map<PlaidAccessToken, PlaidSyncCursor>,
        nextCursors: Map<PlaidAccessToken, PlaidSyncCursor>,
        now: Instant = Instant.now(),
    ) {
        val saved = read()
        val committed = saved.commitFingerprint == cursorFingerprint(cursors)
        val entries = saved.entries
            .filterNot { it.status == Status.applied && committed }
            .associateByTo(linkedMapOf(), Entry::key)

        val modifications = changes.updated.map {
            Entry(it.accountId, it.transactionId, Change.modified, it, firstSeen = now)
        }
        val removals = changes.deleted.map {
            Entry(it.accountId, it.transactionId, Change.removed, firstSeen = now)
        }
        val fresh = (modifications + removals).associateBy(Entry::key)
        for (transaction in changes.created) {
            val key = transaction.accountId to transaction.transactionId
            if (key !in fresh) entries.remove(key)
        }
        val newKeys = mutableSetOf<Pair<PlaidAccountId, String>>()
        for ((key, incoming) in fresh) {
            val previous = entries[key]
            if (previous?.change == incoming.change && previous.transaction == incoming.transaction) continue
            entries[key] = incoming
            newKeys.add(key)
        }

        // Stage supersession before any Firefly writes. Replays merge the same change into its existing entry.
        val staged = State(entries.values.toList())
        var persisted = saved
        if (staged != persisted) {
            write(staged)
            persisted = staged
        }

        val due = entries.values.filter { it.status == Status.pending && it.nextRetry != null && it.nextRetry <= now }
        val (newChanges, backlog) = due.partition { it.key() in newKeys }
        val retries = backlog.sortedBy { it.nextRetry }.take(MAX_RETRIES_PER_POLL)
        for (batch in listOf(newChanges, retries)) {
            val startedAt = TimeSource.Monotonic.markNow()
            for (entry in batch) {
                if (startedAt.elapsedNow() >= 15.seconds) break
                entries[entry.key()] = apply(entry, accountMap, now) { matched ->
                    entries[matched.key()] = matched
                    persisted = State(entries.values.toList())
                    write(persisted)
                }
            }
        }

        // Keep applied entries until the cursor is durable, so a replay can recognize completed writes.
        val processed = staged.copy(
            entries = entries.values.toList(),
            commitFingerprint = cursorFingerprint(nextCursors).takeIf { entries.values.any { it.status == Status.applied } },
        )
        if (processed != persisted) write(processed)
    }

    internal suspend fun finishCursorCommit(cursors: Map<PlaidAccessToken, PlaidSyncCursor>) {
        val saved = read()
        if (saved.commitFingerprint == cursorFingerprint(cursors)) {
            write(State(saved.entries.filterNot { it.status == Status.applied }))
        }
    }

    private suspend fun apply(
        entry: Entry,
        accountMap: Map<PlaidAccountId, FireflyAccountId>,
        now: Instant,
        recordRemovalMatch: suspend (Entry) -> Unit,
    ): Entry {
        val externalId = plaidExternalId(entry.transactionId)
        if (entry.transactionId.isBlank() || !externalId.matches(fireflySearchExternalIdPattern)) {
            return retry(entry, now, "invalid_id", needsReview = true)
        }
        val accountId = accountMap[entry.accountId]?.toString()
            ?: return retry(entry, now, "unmapped_account", needsReview = true)
        val reference = plaidTransferReference(entry.transactionId)
        val matches = fireflyRequest {
            syncHelper.findTransactionsByExternalId(externalId) + syncHelper.findTransactionsByInternalReference(reference)
        } ?: return retry(entry, now, "lookup_failed")
        if (matches.isEmpty()) {
            // An earlier delete may have succeeded before its response or queue checkpoint was saved.
            if (entry.removalMatched) return entry.copy(status = Status.applied, nextRetry = null)
            return retry(entry, now, "no_match")
        }
        val matchesLeg: (TransactionSplit) -> Boolean = { it.externalId == externalId || it.internalReference == reference }
        val groupId = matches.map { it.id }.distinct().singleOrNull() ?: return retry(
            entry.copy(reviewTargets = matches.correctionTargets(matchesLeg)), now, "ambiguous_match", needsReview = true,
        )
        // Search can return only the matching journal of a split group.
        val target = fireflyRequest { syncHelper.getTransactionGroup(groupId) }
            ?: return retry(entry, now, "lookup_failed")
        val reviewEntry = entry.copy(reviewTargets = listOf(target).correctionTargets(matchesLeg))
        if (target.id != groupId || reviewEntry.reviewTargets.isEmpty()) {
            return retry(reviewEntry, now, "target_changed", needsReview = true)
        }
        val split = target.attributes.transactions.singleOrNull()
            ?: return retry(reviewEntry, now, "unsupported_target", needsReview = true)
        if (isPlaidTransferReference(split.internalReference)) {
            return retry(reviewEntry, now, "unsupported_target", needsReview = true)
        }
        val targetAccount = when (split.type) {
            TransactionTypeProperty.withdrawal -> split.sourceId
            TransactionTypeProperty.deposit -> split.destinationId
            else -> return retry(reviewEntry, now, "unsupported_target", needsReview = true)
        }
        if (targetAccount != accountId) return retry(reviewEntry, now, "account_mismatch", needsReview = true)

        var attempt = entry
        val applied = if (entry.change == Change.modified) {
            val update = converter.convertPlaidModification(requireNotNull(entry.transaction), target, accountMap)
            if (update.tx.type != split.type) return retry(reviewEntry, now, "type_change", needsReview = true)
            fireflyRequest { syncHelper.updateBatchInFirefly(listOf(update)) }
        } else {
            if (!entry.removalMatched) {
                attempt = entry.copy(removalMatched = true)
                recordRemovalMatch(attempt)
            }
            fireflyRequest { syncHelper.deleteBatchInFirefly(listOf(target.id)) }
        }
        return if (applied != null) attempt.copy(status = Status.applied, nextRetry = null)
        else retry(attempt, now, "write_failed")
    }

    private fun List<TransactionRead>.correctionTargets(matchesLeg: (TransactionSplit) -> Boolean): List<ReviewTarget> =
        flatMap { group ->
            group.attributes.transactions.filter(matchesLeg).map { ReviewTarget(group.id, it.transactionJournalId) }
        }.distinct()

    private suspend fun <T : Any> fireflyRequest(request: suspend () -> T): T? =
        try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { request() }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            logger.debug("Queued Plaid correction Firefly request failed ({})", failure.javaClass.simpleName)
            null
        }

    private fun retry(entry: Entry, now: Instant, reason: String, needsReview: Boolean = false): Entry {
        val attempts = entry.attempts + 1
        val review = needsReview || attempts >= MAX_ATTEMPTS
        if (review) {
            logger.warn("Plaid correction {} for transaction {} needs review ({})", entry.change, entry.transactionId, reason)
        } else {
            logger.debug("Queued Plaid correction {} for transaction {} ({})", entry.change, entry.transactionId, reason)
        }
        return entry.copy(
            status = if (review) Status.needs_review else Status.pending,
            attempts = attempts,
            nextRetry = if (review) null else now.plusSeconds(60L shl (attempts - 1)),
            lastFailure = reason,
        )
    }

    private suspend fun read(): State = withContext(Dispatchers.IO) {
        val bytes = try {
            Files.readAllBytes(path)
        } catch (_: NoSuchFileException) {
            return@withContext State()
        }
        mapper.readValue<State>(bytes).also {
            check(it.version == 1) { "Unsupported Plaid correction queue version ${it.version}" }
        }
    }

    private suspend fun write(state: State) = withContext(Dispatchers.IO) {
        if (state.entries.isEmpty()) deleteSyncStateFile(path)
        else writeSyncStateFile(path, mapper.writeValueAsBytes(state))
    }
}
