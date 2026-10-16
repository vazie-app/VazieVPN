package app.vazie.vpn.data.links

import android.content.Context
import app.vazie.vpn.core.network.ApiBaseUrl
import app.vazie.vpn.core.network.SessionTokenProvider
import app.vazie.vpn.core.network.VazieApiClient
import java.io.File

/** How `:app` builds what this module provides: the About screen's links and the FAQ screen's questions. The one public door, matching `UpdateStorage`. */
object LinksStorage {

    /** The About screen's links. [baseUrl] is the shared API; the client sends no session token, whoever is
     * signed in. */
    fun repository(context: Context, baseUrl: ApiBaseUrl, filesDir: File): AppLinksRepository = AppLinksRepository(
        source = VazieLinksSource(
            api = VazieApiClient.create(context = context, baseUrl = baseUrl, tokens = SessionTokenProvider { null }),
        ),
        file = File(File(filesDir, DIRECTORY), FILE_NAME),
        nowMillis = System::currentTimeMillis,
    )

    /** The FAQ screen's questions, from the same API and kept beside the links. */
    fun faqRepository(context: Context, baseUrl: ApiBaseUrl, filesDir: File): FaqRepository = FaqRepository(
        source = VazieFaqSource(
            api = VazieApiClient.create(context = context, baseUrl = baseUrl, tokens = SessionTokenProvider { null }),
        ),
        file = File(File(filesDir, DIRECTORY), FAQ_FILE_NAME),
        nowMillis = System::currentTimeMillis,
    )

    private const val DIRECTORY = "vazie-links"
    private const val FILE_NAME = "links.json"
    private const val FAQ_FILE_NAME = "faq.json"
}
