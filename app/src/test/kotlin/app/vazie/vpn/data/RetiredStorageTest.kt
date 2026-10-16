package app.vazie.vpn.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetiredStorageTest {

    @Test
    fun `the retired session and server files go, and the profiles stay`() {
        val filesDir = Files.createTempDirectory("files").toFile()
        val session = filesDir.resolve("vazie-account/session").apply { parentFile.mkdirs(); writeText("sealed") }
        val catalogue = filesDir.resolve("vazie-managed/server-catalogue").apply { parentFile.mkdirs(); writeText("[]") }
        val profiles = filesDir.resolve("vazie-profiles/profiles.bin").apply { parentFile.mkdirs(); writeText("sealed") }

        RetiredStorage.clear(filesDir, keyStore = { null })

        assertFalse(session.parentFile.exists())
        assertFalse(catalogue.parentFile.exists())
        assertTrue(profiles.exists())
        assertEquals("sealed", profiles.readText())
    }

    @Test
    fun `nothing to clear is not a failure`() {
        val filesDir = Files.createTempDirectory("files").toFile()
        RetiredStorage.clear(filesDir, keyStore = { null })
        assertTrue(filesDir.exists())
    }

    @Test
    fun `what AppMetrica left goes, and Vazie's own files and generic names stay`() {
        val dataDir = Files.createTempDirectory("data").toFile()
        fun file(path: String) = dataDir.resolve(path).apply { parentFile.mkdirs(); writeText("x") }
        val leftovers = listOf(
            file("databases/metrica_client_data.db"),
            file("databases/metrica_client_data.db-journal"),
            file("shared_prefs/io.appmetrica.analytics.prefs.xml"),
            file("files/appmetrica_vital_main.dat"),
            file("no_backup/appmetrica_analytics/uuid.dat"),
            file("app_appmetrica_crashes/crash"),
            file("files/ro.yndx.metrica.db.dir/client.db"),
        )
        val kept = listOf(
            file("files/vazie-profiles/profiles.bin"),
            file("files/vazie-app/preferences"),
            file("databases/client.db"),
            file("files/uuid.dat"),
            file("no_backup/androidx.work.workdb"),
        )

        RetiredStorage.clear(dataDir.resolve("files"), keyStore = { null }, dataDir = dataDir)

        leftovers.forEach { assertFalse(it.exists(), "$it should be gone") }
        assertFalse(dataDir.resolve("no_backup/appmetrica_analytics").exists())
        assertFalse(dataDir.resolve("app_appmetrica_crashes").exists())
        kept.forEach { assertTrue(it.exists(), "$it should stay") }
    }
}
