package app.vazie.vpn.data

import app.vazie.vpn.core.designsystem.theme.Appearance
import app.vazie.vpn.core.model.SplitTunnel
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import app.vazie.vpn.core.model.VazieGuideId
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Everything Vazie has to remember, and every way the file can be wrong. */
class FileAppPreferencesTest {

    private val directory: File = Files.createTempDirectory("vazie-preferences").toFile()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `a fresh install starts on the defaults`() {
        val state = preferences().snapshot()

        assertFalse(state.onboardingCompleted)
        assertEquals(VazieAppIcon.Default, state.appIcon)
        assertEquals(Appearance.NIGHT_INDIGO, state.appearance)
    }

    @Test
    fun `the split-tunnel choice survives a new process, and bad package names are dropped`() = runTest {
        preferences().setSplitTunnel(SplitTunnel(SplitTunnel.Mode.EXCLUDE, setOf("com.example.bank", "org.example.maps")))
        assertEquals(
            SplitTunnel(SplitTunnel.Mode.EXCLUDE, setOf("com.example.bank", "org.example.maps")),
            preferences().snapshot().splitTunnel,
        )

        file().writeText(file().readText().replace("split_tunnel_apps=", "split_tunnel_apps=not a package,"))
        assertEquals(setOf("com.example.bank", "org.example.maps"), preferences().snapshot().splitTunnel.packages)
    }

    @Test
    fun `finishing onboarding survives a new process`() = runTest {
        preferences().setOnboardingCompleted(true)

        assertTrue(preferences().snapshot().onboardingCompleted, "a restart forgot onboarding")
    }

    @Test
    fun `the theme choice survives a new process`() = runTest {
        preferences().setAppearance(Appearance.MILK)
        assertEquals(Appearance.MILK, preferences().snapshot().appearance)

        preferences().setAppearance(Appearance.NIGHT_INDIGO)
        assertEquals(Appearance.NIGHT_INDIGO, preferences().snapshot().appearance)
    }

    @Test
    fun `the icon choice survives a new process`() = runTest {
        preferences().setAppIcon(VazieAppIcon.CRIMSON)

        assertEquals(VazieAppIcon.CRIMSON, preferences().snapshot().appIcon)
    }

    /** Earlier builds kept the answer about AppMetrica crash reports; this one has none, so the line is read past and the
     * next write leaves it out. */
    @Test
    fun `the retired crash-report answer is ignored and dropped on the next write`() = runTest {
        write("onboarding_completed=true", "diagnostics_allowed=true")
        assertTrue(preferences().snapshot().onboardingCompleted)

        preferences().setAppearance(Appearance.MILK)

        assertFalse("diagnostics_allowed" in file().readText())
        assertTrue(preferences().snapshot().onboardingCompleted)
    }

    @Test
    fun `the icon plate survives a new process`() = runTest {
        preferences().setAppIconPlate(VazieAppIconPlate.INK)
        assertEquals(VazieAppIconPlate.INK, preferences().snapshot().appIconPlate)
    }

    /** The retired "Match theme" value reads back as the default plate, which then resolves to the mark's
     * signature. */
    @Test
    fun `the retired system plate reads back as the default`() {
        write("app_icon_plate=system")
        assertEquals(VazieAppIconPlate.Default, preferences().snapshot().appIconPlate)
    }

    /** A mark that was renamed still resolves to the mark somebody chose. */
    @Test
    fun `icon ids written under a previous name still resolve`() {
        write("app_icon=lock_terminal")
        assertEquals(VazieAppIcon.ORBIT, preferences().snapshot().appIcon)

        write("app_icon=aurora")
        assertEquals(VazieAppIcon.COTTON_CANDY, preferences().snapshot().appIcon)
    }

    /** A pre-"Маршрут" file opens on Night Indigo and keeps every other setting. */
    @Test
    fun `a file from before the redesign opens on Night Indigo and keeps everything else`() {
        listOf("LAVENDER", "CLOUD", "SYSTEM", "EMBER").forEach { legacy ->
            write(
                "appearance=$legacy",
                "experience=CONSOLE",
                "onboarding_completed=true",
                "app_icon=${VazieAppIcon.LIME.id}",
            )

            val state = preferences().snapshot()

            assertEquals(Appearance.NIGHT_INDIGO, state.appearance, "appearance=$legacy")
                assertTrue(state.onboardingCompleted, "the migration cost an unrelated setting")
            assertEquals(VazieAppIcon.LIME, state.appIcon, "the migration cost the icon")
        }
    }

    @Test
    fun `the next write drops the experience line`() = runTest {
        write("appearance=EMBER", "experience=CONSOLE", "onboarding_completed=true")

        preferences().setAppIcon(VazieAppIcon.CRIMSON)

        val written = file().readText()
        assertFalse(written.lines().any { it.startsWith("experience=") }, "the old mode was written back")
        assertTrue(preferences().snapshot().onboardingCompleted)
    }

    @Test
    fun `every setting is kept when another one changes`() = runTest {
        val store = preferences()
        store.setOnboardingCompleted(true)
        store.setAppIcon(VazieAppIcon.LIME)
        store.setAppIconPlate(VazieAppIconPlate.LIGHT)
        store.setAppearance(Appearance.MILK)

        val reloaded = preferences().snapshot()
        assertTrue(reloaded.onboardingCompleted)
        assertEquals(VazieAppIcon.LIME, reloaded.appIcon)
        assertEquals(VazieAppIconPlate.LIGHT, reloaded.appIconPlate)
        assertEquals(Appearance.MILK, reloaded.appearance)
    }

    @Test
    fun `the stored value is there before anything suspends`() = runTest {
        preferences().setAppIcon(VazieAppIcon.LIME)

        // No `runTest` machinery between construction and the read: this is what the shell does on
        // its first composition, and it has to be able to.
        assertEquals(VazieAppIcon.LIME, preferences().snapshot().appIcon)
    }

    @Test
    fun `observing emits the stored value straight away`() = runTest {
        preferences().setAppIcon(VazieAppIcon.LIME)

        assertEquals(VazieAppIcon.LIME, preferences().observe().first().appIcon)
    }

    @Test
    fun `a corrupt file reads as defaults rather than throwing`() {
        write("this is not a preferences file", "=", "=true", "appearance")

        val state = preferences().snapshot()

        assertFalse(state.onboardingCompleted)
        assertEquals(VazieAppIcon.Default, state.appIcon)
        assertEquals(Appearance.NIGHT_INDIGO, state.appearance)
    }

    @Test
    fun `values this build has never heard of fall back one by one`() {
        write(
            "onboarding_completed=true",
            "app_icon=a_variant_from_the_future",
            "app_icon_plate=holographic_foil",
            "appearance=AURORA_BOREALIS",
            "experience=HOLOGRAPHIC",
        )

        val state = preferences().snapshot()

        assertTrue(state.onboardingCompleted, "an unknown value cost an unrelated setting")
        assertEquals(VazieAppIcon.Default, state.appIcon)
        assertEquals(VazieAppIconPlate.Default, state.appIconPlate)
        assertEquals(Appearance.NIGHT_INDIGO, state.appearance)
    }

    @Test
    fun `a file written by an older build keeps what it does say`() {
        // The format grows by adding lines, so a file from before the theme was stored still has to
        // read cleanly - the settings it does carry are kept and the new ones take their defaults.
        write("onboarding_completed=true", "app_icon=orbit")

        val state = preferences().snapshot()

        assertTrue(state.onboardingCompleted)
        assertEquals(VazieAppIcon.ORBIT, state.appIcon)
        assertEquals(VazieAppIconPlate.Default, state.appIconPlate)
        assertEquals(Appearance.NIGHT_INDIGO, state.appearance)
    }

    @Test
    fun `clearing app data resets it, because the file is the whole state`() = runTest {
        preferences().setOnboardingCompleted(true)
        preferences().setAppearance(Appearance.MILK)

        directory.deleteRecursively()

        val state = preferences().snapshot()
        assertFalse(state.onboardingCompleted)
        assertEquals(Appearance.NIGHT_INDIGO, state.appearance)
    }

    @Test
    fun `nothing secret is written to a file that is not encrypted`() = runTest {
        val store = preferences()
        store.setOnboardingCompleted(true)
        store.setAppIcon(VazieAppIcon.CRIMSON)
        store.setAppIconPlate(VazieAppIconPlate.DEEP)
        store.setAppearance(Appearance.MILK)
        store.acknowledgeGuide(VazieGuideId.WIDGETS)

        val written = file().readText()
        // Everything the unencrypted file may hold, named so that adding a key is a decision.
        val allowed = setOf(
            "onboarding_completed",
            "app_icon",
            "app_icon_plate",
            "appearance",
            "dismissed_hints",
            "notification_explained",
            "tour_state",
            "config_guidance_seen",
            "settings_guidance_seen",
            "split_tunnel_mode",
            "split_tunnel_apps",
        )
        val keys = written.lines()
            .filter { it.isNotBlank() }
            .map { it.substringBefore('=') }
        assertEquals(allowed, keys.toSet(), "an unexpected key reached the plain-text store")
    }

    @Test
    fun `a dismissed hint stays dismissed across a restart`() = runTest {
        // The entire contract of a hint in one assertion. A hint that reappears is worse than one that never
        // appeared: the first time it is a suggestion, the second time it is an argument.
        preferences().acknowledgeGuide(VazieGuideId.WIDGETS)

        val state = preferences().snapshot()
        assertTrue(state.isAcknowledged(VazieGuideId.WIDGETS))
        assertFalse(state.isAcknowledged(VazieGuideId.QUICK_SETTINGS))
        assertFalse(state.isAcknowledged(VazieGuideId.LAUNCHER_SHORTCUTS))
    }

    @Test
    fun `guides are acknowledged one at a time`() = runTest {
        val store = preferences()
        store.acknowledgeGuide(VazieGuideId.WIDGETS)
        store.acknowledgeGuide(VazieGuideId.QUICK_SETTINGS)

        assertEquals(
            setOf(VazieGuideId.WIDGETS, VazieGuideId.QUICK_SETTINGS),
            preferences().snapshot().acknowledgedGuides,
        )
    }

    @Test
    fun `acknowledging twice is not an error and changes nothing`() = runTest {
        val store = preferences()
        store.acknowledgeGuide(VazieGuideId.WIDGETS)
        store.acknowledgeGuide(VazieGuideId.WIDGETS)

        assertEquals(setOf(VazieGuideId.WIDGETS), preferences().snapshot().acknowledgedGuides)
    }

    @Test
    fun `a guide id this build has never heard of is dropped rather than kept`() = runTest {
        // Ids that no longer map to a hint are dropped on read.
        preferences().acknowledgeGuide(VazieGuideId.WIDGETS)
        file().appendText("")
        val written = file().readText()
        file().writeText(
            written.lineSequence().joinToString("\n") { line ->
                if (line.startsWith("dismissed_hints=")) {
                    "dismissed_hints=${VazieGuideId.WIDGETS.id},teleportation"
                } else {
                    line
                }
            },
        )

        assertEquals(setOf(VazieGuideId.WIDGETS), preferences().snapshot().acknowledgedGuides)
    }

    private fun preferences(): AppPreferences = AppStorage.preferences(filesDir = directory)

    private fun file(): File = File(File(directory, AppStorage.DIRECTORY), "preferences")

    private fun write(vararg lines: String) {
        file().parentFile?.mkdirs()
        file().writeText(lines.joinToString(separator = System.lineSeparator()))
    }
}
