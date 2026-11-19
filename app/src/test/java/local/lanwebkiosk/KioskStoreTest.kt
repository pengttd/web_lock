package local.lanwebkiosk

import java.io.DataOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KioskStoreTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun normalizesIpv4AndRejectsInvalidEndpoints() {
        assertEquals("http://192.168.1.10:1/", Endpoint.parse(" 192.168.001.10 ", "1", false)?.url)
        assertEquals("https://10.0.0.8:65535/", Endpoint.parse("10.0.0.8", "65535", true)?.url)
        for (address in listOf("10.0.0.256", "10.0.8", "0x7f.0.0.1", "192.168.1.10/path", "localhost")) {
            assertNull(address, Endpoint.parse(address, "8080", false))
        }
        for (port in listOf("0", "65536", "+8080", "80/path", "")) {
            assertNull(port, Endpoint.parse("10.0.0.8", port, false))
        }
    }

    @Test
    fun reopensSavedConfiguration() {
        val path = directory.root.toPath()
        val store = KioskStore(path)
        val endpoint = requireNotNull(Endpoint.parse("192.168.1.10", "65535", true))
        assertTrue(store.saveEndpoint(endpoint))

        assertEquals("https://192.168.1.10:65535/", KioskStore(path).getEndpoint()?.url)
    }

    @Test
    fun removesLegacyCredentialAndPreservesSavedEndpoint() {
        val path = directory.root.toPath()
        val credentialFile = path.resolve("credential")
        Files.write(credentialFile, ByteArray(49).apply { this[0] = 1 })
        DataOutputStream(Files.newOutputStream(path.resolve("endpoint"))).use { output ->
            output.writeUTF("192.168.1.10")
            output.writeShort(8080)
            output.writeBoolean(false)
        }
        val store = KioskStore(path)
        store.removeLegacyCredential()
        store.removeLegacyCredential()

        assertTrue(Files.notExists(credentialFile))
        assertEquals("http://192.168.1.10:8080/", store.getEndpoint()?.url)
    }
}
