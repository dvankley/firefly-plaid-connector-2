package net.djvk.fireflyPlaidConnector2.sync

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ

internal fun writeSyncStateFile(path: Path, contents: ByteArray) {
    val directory = path.toAbsolutePath().parent
    val temporary = Files.createTempFile(directory, ".${path.fileName}.", ".tmp")
    try {
        temporary.toFile().outputStream().use { output ->
            output.write(contents)
            output.fd.sync()
        }
        Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        forceDirectory(directory)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal fun deleteSyncStateFile(path: Path) {
    if (Files.deleteIfExists(path)) forceDirectory(path.toAbsolutePath().parent)
}

private fun forceDirectory(directory: Path) {
    FileChannel.open(directory, READ).use { it.force(true) }
}
