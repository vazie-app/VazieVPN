package app.vazie.vpn.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.SplitTunnel
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.core.model.VazieGuideId
import app.vazie.vpn.core.model.VazieLink
import app.vazie.vpn.core.model.VazieLinkSection
import app.vazie.vpn.core.model.VazieLinks
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class SettingsUiState(
    val appearance: Appearance,
    val appIcon: VazieAppIcon,
    /** What this device will let Vazie put where. */
    val systemIntegration: SystemIntegrationUi = SystemIntegrationUi(),
    /** Which apps use the tunnel, for the row's subtitle. */
    val splitTunnel: SplitTunnel = SplitTunnel(),
    /** The installed version, for the footer; empty shows the name alone. */
    val versionName: String = "",
    /** An update check is running. */
    val checkingForUpdates: Boolean = false,
    /** Whether there is a support address to open: the backend's first `mailto:` link, or the app's own until the
     * backend has published anything. A published list without it hides the row. */
    val canContactSupport: Boolean = true,
)

/** The build, as three identifiers. */
@Immutable
data class AboutUi(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
)

/** What a launcher icon actually looks like, in the two layers it is made of. */
@Immutable
data class AppIconArt(
    val plate: Brush,
    @param:DrawableRes val foreground: Int,
    /** The ink a flat mark is drawn in, or `null` for a mark that carries its own colour. */
    val foregroundTint: Color? = null,
)

/**
 * The links the app shipped with, shown until the backend has published its own (see [AboutLinksUi]). The backend
 * owns them from then on: these change only with a release.
 */
enum class AboutLink(val url: String) {

    /** The person who writes Vazie, as a person. */
    DEVELOPER("https://t.me/vazovsky17"),

    /** The developer's other work, all on one page. */
    FOLIO("https://folio.link/vazovsky"),

    /** The project's public repository. */
    SOURCE("https://github.com/vazie-app/VazieVPN"),

    /** The project's case on Behance, where an appreciation helps it be seen. */
    BEHANCE("https://www.behance.net/gallery/256711563/Vazie-VPN-Native-Android-VPN-Client"),

    /** Releases and what changed in them. */
    CHANNEL("https://t.me/vazieapp"),

    /**
     * The bug-report form: Vazie Feedback on the site, with the app and the topic chosen and nothing else in the address
     * (no device data, no configuration). Used only until the admin panel's FEEDBACK link is read; it replaced a Google
     * Form, a foreign service the privacy policy does not cover.
     */
    BUG_REPORT("https://vazie.app/contacts?app=vpn&category=bug_report#feedback"),

    /** Questions about the app: the support address the offer (vazie.app/legal/offer) names. */
    SUPPORT_EMAIL("mailto:support@vazie.app"),

    /** Optional support for the developer; it unlocks nothing. */
    BOOSTY("https://boosty.to/vazie"),
}

/** One link as a row shows it: the words already in the device's language. */
@Immutable
data class AboutLinkUi(
    val url: String,
    val title: String,
    val subtitle: String? = null,
    /** The button's words, on the support card. */
    val action: String? = null,
)

/**
 * The About screen's links, card by card, as the backend published them. An empty card is not shown. Built only
 * from a non-empty list: with nothing published the screen shows the links the app shipped with.
 */
@Immutable
data class AboutLinksUi(
    val author: ImmutableList<AboutLinkUi>,
    val project: ImmutableList<AboutLinkUi>,
    val support: ImmutableList<AboutLinkUi>,
    /** The "report a bug" row in Settings. */
    val feedback: AboutLinkUi?,
    val security: ImmutableList<AboutLinkUi> = persistentListOf(),
    val help: ImmutableList<AboutLinkUi> = persistentListOf(),
    /** The «Обратная связь» card on About; its first link is [feedback]. */
    val feedbackLinks: ImmutableList<AboutLinkUi> = persistentListOf(),
    /** The support address — the first published `mailto:` link, whatever its key — for Settings' «Связаться с поддержкой»;
     * `null` when none is published. */
    val supportEmail: AboutLinkUi? = null,
) {
    companion object {
        private const val MAILTO = "mailto:"

        /** [links] in [language] (`ru` or anything else, which reads English). */
        fun from(links: VazieLinks, language: String): AboutLinksUi {
            fun section(section: VazieLinkSection) = links.inSection(section).map { it.toUi(language) }.toImmutableList()
            return AboutLinksUi(
                author = section(VazieLinkSection.AUTHOR),
                project = section(VazieLinkSection.PROJECT),
                support = section(VazieLinkSection.SUPPORT),
                feedback = section(VazieLinkSection.FEEDBACK).firstOrNull(),
                security = section(VazieLinkSection.SECURITY),
                help = section(VazieLinkSection.HELP),
                feedbackLinks = section(VazieLinkSection.FEEDBACK),
                // By what it is, not by its key: the operator may rename a link's key in the admin panel.
                supportEmail = links.links.firstOrNull { it.url.startsWith(MAILTO) }?.toUi(language),
            )
        }

        private fun VazieLink.toUi(language: String) = AboutLinkUi(
            url = url,
            title = title.resolve(language),
            subtitle = subtitle?.resolve(language),
            action = action?.resolve(language),
        )
    }
}

/** Whether this device can be *asked* to add each surface. */
@Immutable
data class SystemIntegrationUi(
    /** Guides whose platform surface is actually shipped by this application variant. */
    val visibleGuides: Set<VazieGuideId> = VazieGuideId.entries.toSet(),
    val canAddWidget: Boolean = false,
    val canAddQuickSettingsTile: Boolean = false,
    /** Whether there is a notification-settings screen to send somebody to. */
    val canOpenNotificationSettings: Boolean = false,
    /** Whether Android will currently let Vazie show its status notification. */
    val notificationsEnabled: Boolean = false,
)

sealed interface SettingsAction {

    /** Ask the launcher to place the Quick Connect widget. */
    data object AddWidget : SettingsAction

    /** Ask System UI to add the Vazie tile. API 33 and up; see [SystemIntegrationUi]. */
    /** [SystemIntegrationUi]. */
    data object AddQuickSettingsTile : SettingsAction

    /** Open Android's notification settings for Vazie. */
    data object OpenNotificationSettings : SettingsAction
    /** Open the place every guide lives, whether or not it has been acknowledged. */
    data object OpenGuides : SettingsAction

    /** The questions and answers, the same as on vazie.app. */
    data object OpenFaq : SettingsAction
    data object OpenAppearance : SettingsAction

    data object OpenAbout : SettingsAction

    /** Choose which apps use the tunnel. */
    data object OpenSplitTunnel : SettingsAction

    /** Open the bug-report form. */
    data object ReportBug : SettingsAction

    /** Write to support at the published address. */
    data object ContactSupport : SettingsAction

    /** Ask the backend whether this build is current; `:app` runs the check and shows what it found. */
    data object CheckForUpdates : SettingsAction
}

/** How Vazie looks: the palette, and the launcher icon on its plate. */
@Immutable
data class AppearanceUiState(
    val appearance: Appearance,
    val appIcon: VazieAppIcon,
    /** The plate as stored; the screen shows it resolved against [appIcon]'s own plates. */
    val appIconPlate: VazieAppIconPlate,
)

sealed interface AppearanceAction {
    data class SelectAppearance(val appearance: Appearance) : AppearanceAction
    data class SelectAppIcon(val icon: VazieAppIcon) : AppearanceAction
    data class SelectAppIconPlate(val plate: VazieAppIconPlate) : AppearanceAction
    data object Back : AppearanceAction
}

@Immutable
data class AboutUiState(
    val about: AboutUi,
    /** What the backend published; `null` shows the links the app shipped with. */
    val links: AboutLinksUi? = null,
)

sealed interface AboutAction {
    data class OpenLink(val url: String) : AboutAction

    /** What Vazie VPN stores, and the diagnostics switch. */
    data object OpenPrivacy : AboutAction
    data object Back : AboutAction
}
