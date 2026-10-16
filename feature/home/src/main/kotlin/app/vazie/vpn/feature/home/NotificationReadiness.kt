package app.vazie.vpn.feature.home

/** Gives Vazie a chance to explain the status notification before Android asks for it — once, ever, and never
 * at the cost of a connection. */
fun interface NotificationReadiness {

    /** [onReady] runs exactly once, on every path, whatever the answer. */
    fun ensure(onReady: () -> Unit)
}
