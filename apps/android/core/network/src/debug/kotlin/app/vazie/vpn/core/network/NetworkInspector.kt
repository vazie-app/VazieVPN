package app.vazie.vpn.core.network

import android.content.Context
import com.chuckerteam.chucker.api.BodyDecoder
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.api.RetentionManager
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.ByteString

/** The on-device HTTP inspector, wired so that it can never store a credential. */
internal object NetworkInspector {

    fun interceptor(context: Context): Interceptor? = ChuckerInterceptor.Builder(context)
        .collector(
            ChuckerCollector(
                context = context,
                // The notification shows method, path and status only; it is the inspector's entry point.
                showNotification = true,
                // One hour: long enough to debug, short enough to keep no history.
                retentionPeriod = RetentionManager.Period.ONE_HOUR,
            ),
        )
        // Vazie's largest response is a server catalogue of a few hundred bytes. 64 KiB is generous
        // for that and small enough that nothing large is ever written to the device.
        .maxContentLength(MAX_CONTENT_LENGTH)
        .redactHeaders(VazieTrafficRedactor.REDACTED_HEADERS)
        .addBodyDecoder(RedactingBodyDecoder)
        // The inspector must not change when bodies are consumed.
        .alwaysReadResponseBody(false)
        .createShortcut(true)
        .build()

    /** Hands Chucker a redacted rendering of every body, and never the original. */
    private object RedactingBodyDecoder : BodyDecoder {

        override fun decodeRequest(request: Request, body: ByteString): String =
            VazieTrafficRedactor.redact(body.utf8(), body.size)

        override fun decodeResponse(response: Response, body: ByteString): String =
            VazieTrafficRedactor.redact(body.utf8(), body.size)
    }

    private const val MAX_CONTENT_LENGTH = 64L * 1024L
}
