package app.vazie.vpn.data.profiles

import app.vazie.vpn.core.crypto.KeystoreCipher
import app.vazie.vpn.api.SelectedProfileStore
import app.vazie.vpn.api.VpnProfileRepository
import java.io.File

/** The module's entire public surface: one function that builds the repository. */
object ProfileStorage {

    /** The subdirectory of `filesDir` the store lives in. */
    const val DIRECTORY: String = "vazie-profiles"

    /** The file holding the selected profile id. Plain by design — see [FileSelectedProfileStore]. */
    private const val SELECTED_FILE_NAME = "selected"

    private const val PROFILE_KEY_ALIAS = "vazie.profiles.v1"

    fun repository(filesDir: File): VpnProfileRepository = EncryptedVpnProfileRepository(
        store = ProfileStore(
            file = File(directory(filesDir), ProfileStore.FILE_NAME),
            // The existing key's alias; changing it would make every stored profile unreadable.
            cipher = KeystoreCipher(alias = PROFILE_KEY_ALIAS),
        ),
    )

    /** The selection, which is a different question from the profiles and therefore a different object:
     * [repository] is the encrypted store, this is one id in one plain file. */
    fun selectedProfileStore(
        filesDir: File,
        profiles: VpnProfileRepository,
    ): SelectedProfileStore = FileSelectedProfileStore(
        file = File(directory(filesDir), SELECTED_FILE_NAME),
        profiles = profiles,
    )

    private fun directory(filesDir: File): File = File(filesDir, DIRECTORY)
}
