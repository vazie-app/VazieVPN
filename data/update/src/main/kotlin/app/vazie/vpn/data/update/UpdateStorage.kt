package app.vazie.vpn.data.update

import android.content.Context
import app.vazie.vpn.core.network.ApiBaseUrl
import app.vazie.vpn.core.network.SessionTokenProvider
import app.vazie.vpn.core.network.VazieApiClient
import app.vazie.vpn.update.api.AppUpdateChecker
import java.io.File

/** How `:app` builds what this module provides. The one public door, matching `AccountStorage`. */
object UpdateStorage {

    /** The update checker for the build whose `versionCode` is [installedVersionCode]. [baseUrl] is the shared API;
     * the client sends no session token, whoever is signed in. */
    fun checker(
        context: Context,
        baseUrl: ApiBaseUrl,
        filesDir: File,
        installedVersionCode: Int,
    ): AppUpdateChecker = AppUpdateChecker(
        installedVersionCode = installedVersionCode,
        source = VazieAppVersionSource(
            api = VazieApiClient.create(context = context, baseUrl = baseUrl, tokens = SessionTokenProvider { null }),
        ),
        store = FileAppUpdateStore(File(File(filesDir, DIRECTORY), STATE_FILE_NAME)),
        nowMillis = System::currentTimeMillis,
    )

    private const val DIRECTORY = "vazie-update"
    private const val STATE_FILE_NAME = "state.json"
}
