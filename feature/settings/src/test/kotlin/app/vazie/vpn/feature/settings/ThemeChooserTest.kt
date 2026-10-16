package app.vazie.vpn.feature.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.R as DesignSystemR
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
import kotlin.test.assertTrue

/** The theme chooser: exactly the two palettes of the "Маршрут" design, and nothing that looks like a
 * presentation mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeChooserTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the chooser offers Night Indigo and Milk, and nothing else`() {
        assertEquals(listOf(Appearance.NIGHT_INDIGO, Appearance.MILK), Appearance.entries)
        render()

        Appearance.entries.forEach { appearance ->
            compose.onNodeWithText(string(appearance.labelRes)).performScrollTo()
        }
    }

    @Test
    fun `the chosen theme reads as chosen and the other does not`() {
        render(selected = Appearance.MILK)

        compose.onNodeWithText(string(Appearance.MILK.labelRes)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(string(Appearance.NIGHT_INDIGO.labelRes)).performScrollTo().assertIsNotSelected()
    }

    @Test
    fun `choosing Milk reports it`() {
        val chosen = mutableListOf<Appearance>()
        render(onAction = { if (it is AppearanceAction.SelectAppearance) chosen += it.appearance })

        compose.onNodeWithText(string(Appearance.MILK.labelRes)).performScrollTo().performClick()

        assertEquals(listOf(Appearance.MILK), chosen)
    }

    /** The modes are gone from the screen, not just from the code. */
    @Test
    fun `no presentation mode is offered`() {
        render()

        listOf("Standard", "Whimsy", "Console").forEach { mode ->
            assertTrue(
                compose.onAllNodes(hasText(mode)).fetchSemanticsNodes().isEmpty(),
                "the Appearance screen still offers the $mode mode",
            )
        }
    }

    @Test
    @Config(qualifiers = "+ru")
    fun `the themes keep their English names in Russian`() {
        assertEquals("Night Indigo", string(Appearance.NIGHT_INDIGO.labelRes))
        assertEquals("Milk", string(Appearance.MILK.labelRes))
    }

    @Test
    @Config(qualifiers = "w280dp-h760dp")
    fun `both themes are reachable on the narrowest supported window`() {
        render()

        Appearance.entries.forEach { appearance ->
            compose.onNodeWithText(string(appearance.labelRes)).performScrollTo()
        }
    }

    @Test
    @Config(fontScale = 1.5f)
    fun `both theme names survive a large font scale`() {
        render()

        Appearance.entries.forEach { appearance ->
            assertTrue(
                compose.onAllNodes(hasText(string(appearance.labelRes))).fetchSemanticsNodes().isNotEmpty(),
                "${appearance.name} lost its label at 1.5x",
            )
        }
    }

    private fun render(
        selected: Appearance = Appearance.NIGHT_INDIGO,
        onAction: (AppearanceAction) -> Unit = {},
    ) {
        compose.setContent {
            VazieTheme(appearance = selected) {
                val standIn = standInPlate()
                AppearanceScreen(
                    state = AppearanceUiState(
                        appearance = selected,
                        appIcon = VazieAppIcon.ORBIT,
                        appIconPlate = VazieAppIconPlate.DEEP,
                    ),
                    appIconArt = { _, _ ->
                        AppIconArt(standIn, DesignSystemR.drawable.ic_vazie_mark)
                    },
                    resolvePlate = { mark, asked -> if (mark.supports(asked)) asked else mark.signaturePlate },
                    onAction = onAction,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun string(id: Int): String = context.getString(id)

    /** See `AppIconPickerTest.standInPlate`: the real plates are resources in `:app`. */
    @Composable
    private fun standInPlate(): Brush = SolidColor(VazieTheme.colors.primary)
}
