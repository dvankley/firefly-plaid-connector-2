package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.io.path.Path

/**
 * Manages the Plaid sync cursors used for transaction synchronization.
 */
class CursorManager(
    cursorFileDirectoryPath: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    val cursorFilePath = Path("$cursorFileDirectoryPath/plaid_sync_cursors.txt")

    /**
     * Reads the cursor map from file storage, if it exists.
     * If it doesn't exist, returns an empty map.
     */
    suspend fun readCursorMap(): MutableMap<PlaidAccessToken, PlaidSyncCursor> {
        return withContext(Dispatchers.IO) {
            val file = cursorFilePath.toFile()
            logger.trace("Reading Plaid sync cursor map from $file")

            if (!file.exists()) {
                logger.trace("No existing Plaid sync cursor map found, starting from scratch")
                return@withContext mutableMapOf()
            }

            file
                .readLines()
                .associate { line ->
                    val (first, second) = line.split("|")
                    Pair(first, second)
                }
                .toMutableMap()
        }
    }

    /**
     * Writes the cursor map to file storage.
     */
    suspend fun writeCursorMap(map: Map<PlaidAccessToken, PlaidSyncCursor>) {
        logger.trace("Writing ${map.size} Plaid sync cursors to map $cursorFilePath")
        return withContext(Dispatchers.IO) {
            writeSyncStateFile(cursorFilePath, serializeCursorMap(map).toByteArray())
        }
    }
}

internal fun serializeCursorMap(map: Map<PlaidAccessToken, PlaidSyncCursor>): String =
    map.entries.filter { it.value.isNotEmpty() }.joinToString("\n") { (token, cursor) -> "$token|$cursor" }

internal fun cursorFingerprint(cursors: Map<PlaidAccessToken, PlaidSyncCursor>): String {
    val bytes = serializeCursorMap(cursors.toSortedMap()).toByteArray()
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
