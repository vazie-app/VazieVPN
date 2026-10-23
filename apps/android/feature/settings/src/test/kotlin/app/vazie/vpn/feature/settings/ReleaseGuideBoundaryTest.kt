package app.vazie.vpn.feature.settings

import app.vazie.vpn.core.model.VazieGuideId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseGuideBoundaryTest {

    @Test
    fun `guides for surfaces absent from a variant are hidden`() {
        val visible = setOf(
            VazieGuideId.LAUNCHER_SHORTCUTS,
            VazieGuideId.NOTIFICATION,
        )

        assertEquals(
            visible.toList(),
            guideList(acknowledged = VazieGuideId.entries.toSet(), visible = visible).map { it.id },
        )
    }
}
