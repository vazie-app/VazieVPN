package app.vazie.vpn.core.model

/** Which mark Vazie is currently wearing in the launcher. */
enum class VazieAppIcon(
    val id: String,
    val signaturePlate: VazieAppIconPlate,
    val plates: List<VazieAppIconPlate>,
) {

    /** `vazie-icon-primary` — the shipping mark: shield-V inside an orbit ring, with a sparkle. */
    ORBIT("orbit", VazieAppIconPlate.DEEP, VIOLET_PLATES),

    /** `vazie-lime` — the primary mark in acid green. */
    LIME("lime", VazieAppIconPlate.FOREST, listOf(VazieAppIconPlate.FOREST, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-emerald` — the primary mark in deep emerald and teal, on its family's Forest field. */
    EMERALD("emerald", VazieAppIconPlate.FOREST, listOf(VazieAppIconPlate.FOREST, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-icon-magenta` — the primary mark in hot pink. */
    MAGENTA("magenta", VazieAppIconPlate.ROSE, listOf(VazieAppIconPlate.ROSE, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-cotton-candy` — the primary mark in pink-and-blue pastel, brightest on Deep. */
    COTTON_CANDY("cotton_candy", VazieAppIconPlate.DEEP, listOf(VazieAppIconPlate.DEEP, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-crimson` — the primary mark in crimson, on Rose. */
    CRIMSON("crimson", VazieAppIconPlate.ROSE, listOf(VazieAppIconPlate.ROSE, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-electric-gold` — the primary mark in electric gold, on its own Amber field. */
    GOLD("gold", VazieAppIconPlate.AMBER, listOf(VazieAppIconPlate.AMBER, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-pearl` — the primary mark in pearl white, on its own Mist field. */
    PEARL("pearl", VazieAppIconPlate.MIST, listOf(VazieAppIconPlate.MIST, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-icon-onyx` — the primary mark in black chrome with a silver rim. */
    ONYX("onyx", VazieAppIconPlate.MIST, listOf(VazieAppIconPlate.MIST, VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),

    /** `vazie-icon-monochrome` — the same shield-V, flat and in one colour. */
    MONOCHROME("monochrome", VazieAppIconPlate.INK, listOf(VazieAppIconPlate.LIGHT, VazieAppIconPlate.INK)),
    ;

    /** Whether this mark may stand on that plate. */
    fun supports(plate: VazieAppIconPlate): Boolean = plate in plates

    companion object {
        val Default: VazieAppIcon = ORBIT

        /** The marks as the picker shows them: grouped by their own field, in [VazieAppIconPlate] order. */
        val byField: List<VazieAppIcon> = entries.sortedBy { it.signaturePlate.ordinal }

        /** Ids that were written to disk under a previous name, and the constant they now mean. */
        internal val LEGACY_IDS: Map<String, VazieAppIcon> = mapOf(
            "lock_terminal" to ORBIT,
            "technical" to ORBIT,
            "guardian" to ORBIT,
            "network" to ORBIT,
            "aurora" to COTTON_CANDY,
        )

        /** Unknown ids resolve to [Default]: a preference file is not a trusted enum. */
        fun fromId(id: String?): VazieAppIcon =
            entries.firstOrNull { it.id == id } ?: LEGACY_IDS[id] ?: Default
    }
}

/** The three fields the violet mark stands on. Every mark: its own field, then Light, then Ink. */
private val VIOLET_PLATES = listOf(
    VazieAppIconPlate.DEEP,
    VazieAppIconPlate.LIGHT,
    VazieAppIconPlate.INK,
)
