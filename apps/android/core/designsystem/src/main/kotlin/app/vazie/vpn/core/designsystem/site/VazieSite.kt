package app.vazie.vpn.core.designsystem.site

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Pages on the Vazie site the app links to. [url] comes from the build (`vazie.site.url`), no trailing slash: https
 * in a release, and a local address while a build is tried on a device. */
@Immutable
data class VazieSite(val url: String) {

    /** A page of the site by its path, such as a FAQ answer's button names. */
    fun page(path: String): String = url + path
}

/** The site this build links to; `:app` provides it from BuildConfig, previews and tests get a placeholder. */
val LocalVazieSite = staticCompositionLocalOf { VazieSite("https://site.example") }
