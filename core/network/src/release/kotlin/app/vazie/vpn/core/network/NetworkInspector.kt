package app.vazie.vpn.core.network

import android.content.Context
import okhttp3.Interceptor

/** No inspector. This is the file that ships. */
internal object NetworkInspector {

    fun interceptor(context: Context): Interceptor? = null
}
