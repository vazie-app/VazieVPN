package app.vazie.vpn.core.navigation

/* Transitions from one feature module into another. */

interface HomeNavigator {
    fun toAddConfig()

    /** The gear on Home; there is no bottom bar since the "Маршрут" redesign. */
    fun toSettings()
}

interface OnboardingNavigator {
    /** Onboarding is finished, not paused: it leaves the back stack for good. */
    fun toMain()

    fun toAddConfig()
}
