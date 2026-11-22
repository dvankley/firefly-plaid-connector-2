package net.djvk.fireflyPlaidConnector2.sync

import com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionSplit
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionStore
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.security.MessageDigest
import java.util.HexFormat

internal data class TransferReplacement(
    val originalId: FireflyTransactionId,
    val original: TransactionSplit,
    val replacement: TransactionStore,
    val completed: Boolean = false,
)

@Component
class TransferReplacementJournal(cursorManager: CursorManager) {
    private val path = cursorManager.cursorFilePath.resolveSibling("pending_transfer_replacements.json")
    private val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(WRITE_DATES_AS_TIMESTAMPS)
        .disable(ADJUST_DATES_TO_CONTEXT_TIME_ZONE)

    private data class State(
        val replacements: List<TransferReplacement>,
        val commitFingerprint: String? = null,
        val version: Int = 1,
    )

    // The polled runner is the sole writer for this cursor directory.
    internal suspend fun pendingFor(cursors: Map<PlaidAccessToken, PlaidSyncCursor>): List<TransferReplacement> =
        withContext(Dispatchers.IO) {
            val state = read() ?: return@withContext emptyList()
            if (state.commitFingerprint == fingerprint(cursors)) {
                deleteSyncStateFile(path)
                return@withContext emptyList()
            }
            state.replacements
        }

    internal suspend fun append(replacements: List<TransferReplacement>) = withContext(Dispatchers.IO) {
        if (replacements.isEmpty()) return@withContext
        val combined = read()?.replacements.orEmpty() + replacements
        check(combined.distinctBy { it.originalId }.size == combined.size) {
            "Multiple pending transfer replacements target the same Firefly transaction"
        }
        write(State(combined))
    }

    internal suspend fun complete(originalId: FireflyTransactionId) = withContext(Dispatchers.IO) {
        val state = checkNotNull(read()) { "Pending transfer replacement journal is missing" }
        check(state.replacements.any { it.originalId == originalId }) { "Missing transfer replacement for $originalId" }
        write(State(state.replacements.map {
            if (it.originalId == originalId) it.copy(completed = true) else it
        }))
    }

    internal suspend fun prepareCursorCommit(cursors: Map<PlaidAccessToken, PlaidSyncCursor>): Boolean =
        withContext(Dispatchers.IO) {
            val state = read() ?: return@withContext false
            check(state.replacements.all { it.completed }) { "Cannot commit cursors with unfinished transfer replacements" }
            write(state.copy(commitFingerprint = fingerprint(cursors)))
            true
        }

    internal suspend fun finishCursorCommit() = withContext(Dispatchers.IO) {
        deleteSyncStateFile(path)
    }

    private fun read(): State? {
        val bytes = try {
            Files.readAllBytes(path)
        } catch (_: NoSuchFileException) {
            return null
        }
        return mapper.readValue<State>(bytes).also {
            check(it.version == 1) { "Unsupported transfer replacement journal version ${it.version}" }
        }
    }

    private fun write(state: State) = writeSyncStateFile(path, mapper.writeValueAsBytes(state))

    private fun fingerprint(cursors: Map<PlaidAccessToken, PlaidSyncCursor>): String {
        val bytes = serializeCursorMap(cursors.toSortedMap()).toByteArray()
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
