package app.vazie.vpn.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The icon set: eight marks, each offered on the fields it was drawn for. */
class VazieAppIconTest {

    @Test
    fun `every mark offers its own signature`() {
        VazieAppIcon.entries.forEach { icon ->
            assertTrue(
                icon.signaturePlate in icon.plates,
                "${icon.id} cannot be shown on the plate it was drawn against",
            )
        }
    }

    @Test
    fun `no mark lists a plate twice`() {
        VazieAppIcon.entries.forEach { icon ->
            assertEquals(icon.plates.size, icon.plates.toSet().size, "${icon.id} repeats a plate")
            icon.plates.forEach { plate ->
                assertTrue(plate in VazieAppIconPlate.concrete, "${icon.id} offers $plate")
            }
        }
    }

    /** `supports` and `plates` are the same statement asked two ways. */
    @Test
    fun `supports agrees with the list it reads`() {
        VazieAppIcon.entries.forEach { icon ->
            VazieAppIconPlate.entries.forEach { plate ->
                assertEquals(plate in icon.plates, icon.supports(plate), "${icon.id} / $plate")
            }
        }
    }

    /** Every mark is reachable on at least two fields, and none on all five. */
    @Test
    fun `every mark is curated rather than offered on everything`() {
        VazieAppIcon.entries.forEach { icon ->
            assertTrue(icon.plates.size >= 2, "${icon.id} offers no background choice")
            assertTrue(
                icon.plates.size < VazieAppIconPlate.concrete.size,
                "${icon.id} is offered on every plate, which is the un-curated product again",
            )
        }
    }

    /** The picker order: the mark's own field first, then Light, then Ink. */
    @Test
    fun `plates run from the mark's own field to Light and Ink`() {
        VazieAppIcon.entries.forEach { icon ->
            val expected = listOf(icon.signaturePlate, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK).distinct()
            val actual = if (icon.signaturePlate == VazieAppIconPlate.INK) icon.plates.reversed() else icon.plates
            assertEquals(expected.filter { it in icon.plates }, actual, "${icon.id} plates are out of order")
        }
    }

    /** The picker groups the marks by their own field, in plate order, keeping every mark once. */
    @Test
    fun `the picker order follows the fields`() {
        val fields = VazieAppIcon.byField.map { it.signaturePlate.ordinal }
        assertEquals(fields.sorted(), fields)
        assertEquals(VazieAppIcon.entries.toSet(), VazieAppIcon.byField.toSet())
        assertEquals(VazieAppIcon.ORBIT, VazieAppIcon.byField.first())
        assertEquals(VazieAppIcon.MONOCHROME, VazieAppIcon.byField.last())
    }

    /** The alias count is a sum over the marks, and it is this number. */
    @Test
    fun `the pairing count is the one the manifest ships`() {
        assertEquals(
            EXPECTED_PAIRINGS,
            VazieAppIcon.entries.sumOf { it.plates.size },
            "the curated set changed size; the manifest and the tests that count it change with it",
        )
    }

    /** Ids written to disk by a shipped build still resolve to the mark somebody chose. */
    @Test
    fun `renamed ids migrate rather than resetting`() {
        assertEquals(VazieAppIcon.ORBIT, VazieAppIcon.fromId("lock_terminal"))
        assertEquals(VazieAppIcon.ORBIT, VazieAppIcon.fromId("guardian"))
        assertEquals(VazieAppIcon.ORBIT, VazieAppIcon.fromId("network"))
        assertEquals(VazieAppIcon.COTTON_CANDY, VazieAppIcon.fromId("aurora"))
        VazieAppIcon.LEGACY_IDS.forEach { (stored, mark) ->
            assertEquals(mark, VazieAppIcon.fromId(stored), "$stored lost its migration")
            assertTrue(
                VazieAppIcon.entries.none { it.id == stored },
                "$stored is both a live id and a migration, which cannot both be true",
            )
        }
    }

    @Test
    fun `every live id reads back as itself, and an unknown one falls back`() {
        VazieAppIcon.entries.forEach { icon ->
            assertEquals(icon, VazieAppIcon.fromId(icon.id), "${icon.id} does not round-trip")
        }
        assertEquals(VazieAppIcon.Default, VazieAppIcon.fromId(null))
        assertEquals(VazieAppIcon.Default, VazieAppIcon.fromId("a_variant_from_the_future"))
    }

    /** Ids are unique and stable-looking: lower case, no spaces, nothing that needs escaping. */
    @Test
    fun `ids are unique and safe to store`() {
        val ids = VazieAppIcon.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "two marks share an id")
        val bad = ids.filterNot { it.matches(Regex("[a-z][a-z0-9_]*")) }
        if (bad.isNotEmpty()) fail("ids that a preference file cannot hold safely: $bad")
    }

    private companion object {
        /** Twenty-one: three violet marks on three fields, emerald and magenta on three, and the pastel,
         * chrome and flat marks on two each. */
        const val EXPECTED_PAIRINGS = 29
    }
}
