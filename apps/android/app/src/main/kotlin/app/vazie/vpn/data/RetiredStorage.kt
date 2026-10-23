package app.vazie.vpn.data

import java.io.File
import java.security.KeyStore

/**
 * What builds before this one kept and this one has no use for: a signed-in session, encrypted under its own
 * Keystore key, a cached list of servers, and whatever the AppMetrica SDK (crash reports, in earlier builds) left
 * behind — its device id, queued reports and settings. Removed at start, quietly; nothing is read from it first.
 */
internal object RetiredStorage {

    private val DIRECTORIES = listOf("vazie-account", "vazie-managed")
    private const val SESSION_KEY_ALIAS = "vazie.account.session.v1"

    /** Where an Android app keeps files, under its data directory. */
    private val SDK_HOMES = listOf("databases", "shared_prefs", "files", "no_backup", "cache")

    /**
     * The names AppMetrica 8.x gives its databases, preferences and folders (`metrica_client_data.db`,
     * `appmetrica_vital_main.dat`, `io.appmetrica.analytics.*`, `ro.yndx.metrica.db.dir`). Only these prefixes, so
     * nothing of Vazie's own (`vazie-*`) and no generic name such as `client.db` is ever matched.
     */
    private val SDK_PREFIXES = listOf("appmetrica", "metrica", "io.appmetrica", "ro.yndx")

    fun clear(filesDir: File, keyStore: () -> KeyStore? = ::androidKeyStore, dataDir: File? = null) {
        DIRECTORIES.forEach { name -> File(filesDir, name).takeIf { it.exists() }?.deleteRecursively() }
        runCatching { keyStore()?.takeIf { it.containsAlias(SESSION_KEY_ALIAS) }?.deleteEntry(SESSION_KEY_ALIAS) }
        dataDir?.let(::clearAppMetrica)
    }

    private fun clearAppMetrica(dataDir: File) {
        SDK_HOMES.forEach { home -> File(dataDir, home).deleteMatching { name -> name } }
        // `Context.getDir("appmetrica…")` folders sit beside the homes as `app_appmetrica…`.
        dataDir.deleteMatching { name -> name.removePrefix("app_") }
    }

    private fun File.deleteMatching(nameOf: (String) -> String) {
        listFiles { file -> SDK_PREFIXES.any { nameOf(file.name.lowercase()).startsWith(it) } }
            .orEmpty()
            .forEach { runCatching { it.deleteRecursively() } }
    }

    private fun androidKeyStore(): KeyStore? =
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }.getOrNull()
}
