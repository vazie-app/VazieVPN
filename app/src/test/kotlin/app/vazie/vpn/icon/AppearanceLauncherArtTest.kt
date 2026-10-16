package app.vazie.vpn.icon

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import app.vazie.vpn.core.designsystem.component.VazieIconChoice
import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.designsystem.theme.VazieTheme
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Every piece of launcher artwork the Appearance screen draws has to be loadable by Compose. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppearanceLauncherArtTest {

    @get:Rule
    val compose = createComposeRule()

    /** The exact call the picker makes, for every cell of the matrix. */
    @Test
    fun `every mark on every plate can be drawn by the picker`() {
        compose.setContent {
            VazieTheme(appearance = Appearance.NIGHT_INDIGO) {
                Column {
                    VazieAppIcon.entries.forEach { icon ->
                        icon.plates.forEach { plate ->
                            // `painterResource` is where the crash was: it is the one call that
                            // reads the resource rather than the id.
                            painterResource(LauncherIconArt.foreground(icon, plate))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The whole tile, assembled the way the screen assembles it. */
    @Test
    fun `every tile the Appearance screen can show composes`() {
        compose.setContent {
            VazieTheme(appearance = Appearance.NIGHT_INDIGO) {
                val colours = LauncherPlateColours.read()
                Column {
                    VazieAppIcon.entries.forEach { icon ->
                        icon.plates.forEach { plate -> Tile(icon, plate, colours) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Any stored plate resolves into the mark's own list, so it always has an alias behind it. */
    @Test
    fun `a stored plate never resolves to a pair with no alias`() {
        VazieAppIconPlate.entries.forEach { asked ->
            VazieAppIcon.entries.forEach { icon ->
                val resolved = LauncherIconArt.resolvePlate(asked, icon)
                assertTrue(icon.supports(resolved), "$asked resolved ${icon.id} to $resolved")
                VazieLauncherFace(icon, resolved)
            }
        }
    }

    /** An explicit plate is returned untouched, so a person's choice is never quietly re-resolved. */
    @Test
    fun `an explicit plate the mark offers survives the rule`() {
        VazieAppIcon.entries.forEach { icon ->
            icon.plates.forEach { plate ->
                assertEquals(
                    plate,
                    LauncherIconArt.resolvePlate(plate, icon),
                    "an explicit choice was re-resolved for ${icon.id}",
                )
            }
        }
    }

    /** Switching to a mark that does not offer the stored plate lands on that mark's signature. */
    @Test
    fun `a plate the new mark does not offer clamps to its signature`() {
        val resolved = LauncherIconArt.resolvePlate(VazieAppIconPlate.DEEP, VazieAppIcon.ONYX)
        assertEquals(VazieAppIcon.ONYX.signaturePlate, resolved)
        assertTrue(!VazieAppIcon.ONYX.supports(VazieAppIconPlate.DEEP))
    }

    /** A pair outside the curated set is refused rather than silently addressed. */
    @Test
    fun `a launcher face refuses a pairing with no alias`() {
        assertFailsWith<IllegalArgumentException> {
            VazieLauncherFace(VazieAppIcon.ONYX, VazieAppIconPlate.FOREST)
        }
    }

    /** A fresh install stays on the alias the manifest ships enabled. */
    @Test
    fun `default preferences resolve to the face the manifest ships enabled`() {
        val resolved = LauncherIconArt.resolvePlate(VazieAppIconPlate.Default, VazieAppIcon.Default)
        assertEquals(
            VazieLauncherFace.Default,
            VazieLauncherFace(VazieAppIcon.Default, resolved),
            "a fresh install reconciles off the alias the manifest enables",
        )
    }

    /** The retired `system` value and anything unknown land on the mark's signature. */
    @Test
    fun `a retired plate id lands on the signature`() {
        VazieAppIcon.entries.forEach { icon ->
            val resolved = LauncherIconArt.resolvePlate(VazieAppIconPlate.fromId("system"), icon)
            assertTrue(icon.supports(resolved), "${icon.id} resolved to $resolved")
        }
        assertEquals(
            VazieAppIcon.ONYX.signaturePlate,
            LauncherIconArt.resolvePlate(VazieAppIconPlate.fromId("system"), VazieAppIcon.ONYX),
        )
    }

    @Composable
    private fun Tile(
        icon: VazieAppIcon,
        plate: VazieAppIconPlate,
        colours: LauncherPlateColours,
    ) {
        val art = LauncherIconArt.art(icon, plate, colours)
        VazieIconChoice(
            label = "${icon.id}/${plate.id}",
            plate = art.plate,
            foreground = art.foreground,
            foregroundTint = art.foregroundTint,
            selected = icon == VazieAppIcon.Default,
            onSelect = {},
        )
    }
}
