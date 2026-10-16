package app.vazie.vpn.feature.settings

import androidx.compose.ui.test.isHeading
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.core.designsystem.R as DesignSystemR
import app.vazie.vpn.core.designsystem.preview.VaziePreviewTheme
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

/** The icon picker shows what a person will actually get. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 360dp is the width the bug appeared at and the narrowest mainstream phone; stating it here is what
// makes the geometry assertion below mean something rather than depend on Robolectric's default.
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class AppIconPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every mark and every plate is offered, and the flat mark among them`() {
        render()

        VazieAppIcon.entries.forEach { icon ->
            choice(string(icon.labelRes)).performScrollTo()
        }
        // The plate row offers only the chosen mark's own plates.
        VazieAppIcon.ORBIT.plates.forEach { plate ->
            choice(string(plate.labelRes)).performScrollTo()
        }
        assertEquals(
            0,
            compose.onAllNodes(hasText(string(VazieAppIconPlate.FOREST.labelRes)))
                .fetchSemanticsNodes().size,
            "a plate the chosen mark does not offer was drawn anyway",
        )
        // Named rather than counted: the flat mark is the one this pass added, and the reason it is a choice
        // at all is that a themed launcher layer is the system's decision and this is the person's.
        choice(string(VazieAppIcon.MONOCHROME.labelRes)).performScrollTo()
    }

    /** Every mark and every plate is laid out *inside the screen*, and the marks wrap. */
    /** Nothing on this screen scrolls sideways. */
    @Test
    fun `no chooser on this screen hides its options behind a horizontal scroll`() {
        render()
        val sideways = compose
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
        assertEquals(
            0,
            sideways.size,
            "the appearance screen has ${sideways.size} horizontally scrolling container(s). " +
                "Every chooser here has to lay its options out where they can be seen.",
        )
    }

    @Test
    fun `the chosen mark and the chosen plate are the selected ones`() {
        render(icon = VazieAppIcon.LIME, plate = VazieAppIconPlate.LIGHT)

        choice(string(VazieAppIcon.LIME.labelRes)).performScrollTo()
            .assertIsSelected()
        choice(string(VazieAppIcon.ORBIT.labelRes)).performScrollTo()
            .assertIsNotSelected()
        choice(string(VazieAppIconPlate.LIGHT.labelRes)).performScrollTo()
            .assertIsSelected()
        choice(string(VazieAppIconPlate.FOREST.labelRes)).performScrollTo()
            .assertIsNotSelected()
    }

    /** The mark tiles are drawn on the plate in force, and the plate tiles on the mark in force. */
    @Test
    fun `each row previews against the other row's current choice`() {
        val asked = mutableListOf<Pair<VazieAppIcon, VazieAppIconPlate>>()
        render(icon = VazieAppIcon.CRIMSON, plate = VazieAppIconPlate.INK, record = asked)

        // Every mark is drawn on a plate it actually offers.
        val marksDrawn = asked.map { it.first }.toSet()
        assertTrue(
            marksDrawn.containsAll(VazieAppIcon.entries),
            "the mark row did not draw every mark: $marksDrawn",
        )
        asked.forEach { (mark, plate) ->
            assertTrue(mark.supports(plate), "${mark.id} was previewed on ${plate.id}")
        }

        val platesDrawn = asked.filter { it.first == VazieAppIcon.CRIMSON }.map { it.second }
        assertTrue(
            platesDrawn.containsAll(VazieAppIcon.CRIMSON.plates),
            "the plate row did not draw the chosen mark on every plate it offers: $platesDrawn",
        )
    }

    /** Choosing a mark whose signature is Ink must not turn every other mark black. */
    @Test
    fun `other marks keep their own signature plate whatever plate is chosen`() {
        val asked = mutableListOf<Pair<VazieAppIcon, VazieAppIconPlate>>()
        render(icon = VazieAppIcon.EMERALD, plate = VazieAppIconPlate.INK, record = asked)

        VazieAppIcon.entries.filter { it != VazieAppIcon.EMERALD }.forEach { mark ->
            assertTrue(
                mark to mark.signaturePlate in asked,
                "${mark.id} was not previewed on its signature ${mark.signaturePlate.id}: $asked",
            )
        }
        assertTrue(VazieAppIcon.ORBIT to VazieAppIconPlate.INK !in asked, "Orbit was previewed on Ink")
        assertTrue(VazieAppIcon.EMERALD to VazieAppIconPlate.INK in asked)
    }

    /** A stored plate the chosen mark does not offer still leaves a tile selected. */
    @Test
    fun `an unsupported stored plate still shows the mark's signature as selected`() {
        render(icon = VazieAppIcon.ONYX, plate = VazieAppIconPlate.DEEP)

        choice(string(VazieAppIcon.ONYX.signaturePlate.labelRes))
            .performScrollTo()
            .assertIsSelected()
        assertEquals(
            0,
            compose.onAllNodes(hasText(string(VazieAppIconPlate.DEEP.labelRes)))
                .fetchSemanticsNodes().size,
            "a plate Onyx does not offer was drawn in its row",
        )
    }

    @Test
    fun `choosing emits the mark and the plate as separate decisions`() {
        val actions = mutableListOf<AppearanceAction>()
        render(onAction = { actions += it })

        // Clicked through the semantics action: tiles far along a row are only partly on screen.
        choice(string(VazieAppIcon.CRIMSON.labelRes))
            .performSemanticsAction(SemanticsActions.OnClick)
        choice(string(VazieAppIconPlate.INK.labelRes))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            listOf(
                AppearanceAction.SelectAppIcon(VazieAppIcon.CRIMSON),
                AppearanceAction.SelectAppIconPlate(VazieAppIconPlate.INK),
            ),
            actions,
        )
    }

    private fun render(
        icon: VazieAppIcon = VazieAppIcon.ORBIT,
        plate: VazieAppIconPlate = VazieAppIconPlate.DEEP,
        record: MutableList<Pair<VazieAppIcon, VazieAppIconPlate>>? = null,
        onAction: (AppearanceAction) -> Unit = {},
    ) {
        compose.setContent {
            VaziePreviewTheme(Appearance.NIGHT_INDIGO) {
                // Read in composition and captured, because the lambda below is not composable —
                // the same shape `:app` uses, and for the same reason.
                val standIn = standInPlate()
                AppearanceScreen(
                    state = AppearanceUiState(
                        appearance = Appearance.NIGHT_INDIGO,
                        appIcon = icon,
                        appIconPlate = plate,
                    ),
                    appIconArt = { mark, on ->
                        record?.add(mark to on)
                        AppIconArt(standIn, DesignSystemR.drawable.ic_vazie_mark)
                    },
                    // Mirrors what `LauncherIconArt` does: clamp anything the mark does not offer
                    // to its signature.
                    resolvePlate = { mark, asked -> if (mark.supports(asked)) asked else mark.signaturePlate },
                    onAction = onAction,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun string(id: Int): String = context.getString(id)

    /** A stand-in plate. */
    @Composable
    private fun standInPlate(): Brush = SolidColor(VazieTheme.colors.primary)

    /** A choice row, not the group heading that may carry the same word. */
    private fun choice(text: String) = compose.onNode(hasText(text) and !isHeading())
}
