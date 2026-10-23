package app.vazie.vpn

/** One thing a build variant wants done at process start, off the main thread. */
fun interface StartupTask {
    suspend fun run()
}
