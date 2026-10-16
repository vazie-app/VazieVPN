package app.vazie.vpn.core.designsystem.theme

/** Which palette paints the UI. The "Маршрут" design has two, and nothing else to choose: there are no
 * presentation modes. */
enum class Appearance {
    NIGHT_INDIGO,
    MILK,
    ;

    companion object {

        /** What a new installation shows. */
        val Default: Appearance = NIGHT_INDIGO

        /** Read a stored appearance name. */
        fun fromStoredName(name: String?): Appearance =
            if (name == MILK.name) MILK else Default
    }
}
