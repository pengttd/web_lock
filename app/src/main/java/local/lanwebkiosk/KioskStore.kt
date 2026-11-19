package local.lanwebkiosk

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class KioskStore(directory: Path?) {
    constructor(context: Context) : this(context.noBackupFilesDir?.toPath())

    private val credentialFile = directory?.resolve("credential")
    private val endpointFile = directory?.resolve("endpoint")

    fun removeLegacyCredential() {
        val path = credentialFile ?: return
        storageResult(Unit) { Files.deleteIfExists(path) }
    }

    fun getEndpoint(): Endpoint? {
        val path = endpointFile ?: return null
        return storageResult(null) {
            DataInputStream(Files.newInputStream(path)).use { input ->
                val endpoint = Endpoint.parse(input.readUTF(), input.readUnsignedShort().toString(), input.readBoolean())
                if (input.read() == -1) endpoint else null
            }
        }
    }

    fun saveEndpoint(endpoint: Endpoint): Boolean {
        val path = endpointFile ?: return false
        return writeAtomically(path) { output ->
            output.writeUTF(endpoint.ip)
            output.writeShort(endpoint.port)
            output.writeBoolean(endpoint.https)
        }
    }

    private fun writeAtomically(path: Path, write: (DataOutputStream) -> Unit): Boolean = storageResult(false) {
        val pending = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp")
        try {
            FileOutputStream(pending.toFile()).use { file ->
                val output = DataOutputStream(file)
                write(output)
                output.flush()
                file.fd.sync()
            }
            Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        } finally {
            storageResult(Unit) { Files.deleteIfExists(pending) }
        }
    }

    private inline fun <T> storageResult(fallback: T, operation: () -> T): T = try {
        operation()
    } catch (_: IOException) {
        fallback
    } catch (_: SecurityException) {
        fallback
    }
}
