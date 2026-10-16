package app.vazie.vpn.icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.data.AppPreferencesState
import app.vazie.vpn.feature.settings.AppearanceScreen
import app.vazie.vpn.feature.settings.AppearanceUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Opening Appearance works, from every state a stored preference file can put the app in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppearanceScreenLaunchTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a fresh install opens Appearance`() {
        open(AppPreferencesState())
    }

    /** A file written before the plate existed: no `app_icon_plate` line at all. */
    @Test
    fun `preferences written before the plate existed open Appearance`() {
        open(AppPreferencesState(appIcon = VazieAppIcon.ORBIT))
        assertEquals(VazieAppIconPlate.DEEP, AppPreferencesState().appIconPlate)
    }

    /** A stored icon id from before the rename resolves, and the screen opens on it. */
    @Test
    fun `a legacy icon id opens Appearance`() {
        open(
            *listOf("lock_terminal", "technical", "guardian", "network", "aurora", "system")
                .map { AppPreferencesState(appIcon = VazieAppIcon.fromId(it)) }
                .toTypedArray(),
        )
    }

    /** The merged appearance opens Appearance, and shows the palette it migrated to as selected. */
    @Test
    fun `the merged Cloud appearance opens Appearance`() {
        val migrated = Appearance.fromStoredName("CLOUD")
        assertEquals(Appearance.NIGHT_INDIGO, migrated)
        open(AppPreferencesState(appearance = migrated))
    }

    /** Values this build has never heard of fall back and still draw. */
    @Test
    fun `unknown stored values open Appearance`() {
        open(
            AppPreferencesState(
                appIcon = VazieAppIcon.fromId("a_variant_from_the_future"),
                appIconPlate = VazieAppIconPlate.fromId("holographic_foil"),
                appearance = Appearance.fromStoredName("AURORA_BOREALIS"),
            ),
        )
    }

    /** Every mark opens the screen, on every plate it offers and on the rule. */
    @Test
    fun `every mark on every plate it offers opens Appearance`() {
        val states = VazieAppIcon.entries.flatMap { icon ->
            (icon.plates + VazieAppIconPlate.fromId("system")).map { plate ->
                AppPreferencesState(appIcon = icon, appIconPlate = plate)
            }
        }
        open(*states.toTypedArray())
    }

    /** Every appearance opens the screen. */
    @Test
    fun `every appearance opens Appearance`() {
        open(*Appearance.entries.map { AppPreferencesState(appearance = it) }.toTypedArray())
    }

    /** Compose the screen exactly as `VazieNavHost` does. */
    private fun open(vararg states: AppPreferencesState) {
        val current = mutableStateOf(states.first())
        compose.setContent {
            val stored = current.value
            VazieTheme(appearance = stored.appearance) {
                Screen(stored)
            }
        }
        states.forEach { state ->
            current.value = state
            compose.waitForIdle()
        }
    }

    @Composable
    private fun Screen(stored: AppPreferencesState) {
        val colours = LauncherPlateColours.read()
        AppearanceScreen(
            state = AppearanceUiState(
                appearance = stored.appearance,
                appIcon = stored.appIcon,
                appIconPlate = stored.appIconPlate,
            ),
            appIconArt = { icon, plate -> LauncherIconArt.art(icon, plate, colours) },
            resolvePlate = { icon, plate -> LauncherIconArt.resolvePlate(plate, icon) },
            onAction = {},
        )
    }
}
