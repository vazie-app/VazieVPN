package app.vazie.vpn.core.model

/** The field a launcher mark stands on, in picker order: the marks' own fields, then Light and Ink. */
enum class VazieAppIconPlate(val id: String) {

    /** The canonical Vazie violet field. Signature of the violet mark. */
    DEEP("deep"),

    /** A deep green field. Signature of the lime and emerald marks. */
    FOREST("forest"),

    /** A deep magenta field, derived the same way. Signature of the magenta and crimson marks. */
    ROSE("rose"),

    /** A dark bronze field derived from the gold mark. Its signature. */
    AMBER("amber"),

    /** A grey-lavender field derived from the pearl mark. Signature of the pearl and onyx marks. */
    MIST("mist"),

    /** Lavender's own background, as a plate. The one bright field. */
    LIGHT("light"),

    /** Flat near-black. Signature of the flat mark, and neutral under everything. */
    INK("ink"),
    ;

    /** Whether content on this plate has to be dark to be seen — which ink the flat mark takes. */
    val isLight: Boolean get() = this == LIGHT

    companion object {
        /** The violet mark's signature, and what an unknown stored id falls back to. */
        val Default: VazieAppIconPlate = DEEP

        /** Every plate, for the checks that sweep the whole matrix. */
        val concrete: List<VazieAppIconPlate> = entries

        /** Unknown ids resolve to [Default]: a preference file is not a trusted enum. */
        fun fromId(id: String?): VazieAppIconPlate = entries.firstOrNull { it.id == id } ?: Default
    }
}
