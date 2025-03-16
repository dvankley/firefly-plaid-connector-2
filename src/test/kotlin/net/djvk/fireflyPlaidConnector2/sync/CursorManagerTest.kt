package net.djvk.fireflyPlaidConnector2.sync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

class CursorManagerTest {

    @Test
    fun `test read and write cursor map`(@TempDir tempDir: Path) = runBlocking {
        // Setup
        val cursorFilePath = tempDir.resolve("test_cursors.txt")
        val cursorManager = CursorManager(tempDir.toString())
        
        // Initial map should be empty
        val initialMap = cursorManager.readCursorMap()
        assertEquals<Map<String, String>>(emptyMap(), initialMap)
        
        // Write some data
        val testMap = mutableMapOf(
            "access_token_1" to "cursor_1",
            "access_token_2" to "cursor_2"
        )
        cursorManager.writeCursorMap(testMap)
        
        // Read it back and verify
        val readMap = cursorManager.readCursorMap()
        assertEquals<Map<String, String>>(testMap, readMap)
    }
    
    @Test
    fun `test write cursor map filters empty cursors`(@TempDir tempDir: Path) = runBlocking {
        // Setup
        val cursorManager = CursorManager(tempDir.toString())
        
        // Map with empty cursor
        val testMap = mutableMapOf(
            "access_token_1" to "cursor_1",
            "access_token_2" to "" // Empty cursor should be filtered out
        )
        
        // Write the map
        cursorManager.writeCursorMap(testMap)
        
        // Read it back and verify empty cursor was filtered
        val readMap = cursorManager.readCursorMap()
        assertEquals(1, readMap.size)
        assertEquals("cursor_1", readMap["access_token_1"])
        assertEquals(null, readMap["access_token_2"])
    }
}