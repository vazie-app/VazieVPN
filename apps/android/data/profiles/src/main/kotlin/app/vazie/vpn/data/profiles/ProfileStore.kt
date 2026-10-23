package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.crypto.ByteCipher

import app.vazie.vpn.api.VpnProfile
import java.io.File
import kotlinx.serialization.json.Json

/** The profile file: one encrypted document holding every stored profile. */
internal class ProfileStore(
    private val file: File,
    private val cipher: ByteCipher,
    private val json: Json = DEFAULT_JSON,
) {

    /** The stored profiles, an empty list when there is no file, or `null` when it is unreadable. */
    fun read(): List<VpnProfile>? {
        if (!file.exists()) return emptyList()
        return try {
            val document = json.decodeFromString<StoredProfiles>(
                cipher.decrypt(file.readBytes()).toString(Charsets.UTF_8),
            )
            if (document.version != StoredProfiles.CURRENT_VERSION) return null
            document.profiles.map { it.toProfile() }
        } catch (failure: Exception) {
            // The failure is not rethrown and not logged. Every exception this can produce carries
            // the bytes or the field that failed, and those bytes are the user's configuration.
            null
        }
    }

    fun write(profiles: List<VpnProfile>) {
        val document = StoredProfiles(profiles = profiles.map { it.toRecord() })
        val bytes = cipher.encrypt(json.encodeToString(document).toByteArray(Charsets.UTF_8))
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + TEMPORARY_SUFFIX)
        temporary.writeBytes(bytes)
        check(temporary.renameTo(file)) { "could not replace the profile store" }
    }

    companion object {
        const val FILE_NAME = "profiles.bin"

        private const val TEMPORARY_SUFFIX = ".tmp"

        private val DEFAULT_JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
