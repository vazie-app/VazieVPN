package app.vazie.vpn.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ids are a storage format, and a storage format is a promise about the past. */
class VazieGuideIdTest {

    @Test
    fun `the stored ids are what they have always been`() {
        assertEquals(
            mapOf(
                VazieGuideId.QUICK_SETTINGS to "quick_settings",
                VazieGuideId.LAUNCHER_SHORTCUTS to "launcher_shortcuts",
                VazieGuideId.WIDGETS to "widgets",
                VazieGuideId.NOTIFICATION to "notification",
            ),
            VazieGuideId.entries.associateWith { it.id },
        )
    }

    @Test
    fun `every id round-trips`() {
        VazieGuideId.entries.forEach { guide ->
            assertEquals(guide, VazieGuideId.fromId(guide.id))
        }
    }

    @Test
    fun `an id this build does not know is null, not a guess and not a crash`() {
        // The file can hold an id from a newer build, or from one where a guide was named differently.
        // Neither is worth a crash, and neither may be silently mapped onto a guide it is not.
        assertNull(VazieGuideId.fromId("teleportation"))
        assertNull(VazieGuideId.fromId(""))
        assertNull(VazieGuideId.fromId("WIDGETS"))
    }

    @Test
    fun `no two guides share an id`() {
        val ids = VazieGuideId.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "two guides would overwrite each other: $ids")
    }

    @Test
    fun `no id contains the separator the file joins them with`() {
        // The acknowledged set is stored as one comma-joined line. An id containing a comma would
        // split into two ids on the way back, one of which would be unrecognised and dropped.
        VazieGuideId.entries.forEach { guide ->
            assertTrue(',' !in guide.id, "${guide.name} would not survive being stored")
            assertTrue(guide.id.isNotBlank(), "${guide.name} has no id")
        }
    }

    /** The one thing the kind governs, and the one thing it must not. */
    @Test
    fun `only a setup guide may ask the platform for anything`() {
        assertTrue(VazieGuideKind.SETUP.allowsPlatformAction)
        assertFalse(
            VazieGuideKind.USAGE.allowsPlatformAction,
            "a usage guide with a platform action would be inventing a setup step",
        )
        assertEquals(VazieGuideKind.USAGE, VazieGuideId.LAUNCHER_SHORTCUTS.kind)
    }
}
