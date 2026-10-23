package app.vazie.vpn.runtime

import org.junit.Test

/** Regression coverage for Android's Service construction order. */
class VazieVpnServiceTest {

    @Test
    fun constructorDoesNotReadContextBeforeAndroidAttachesIt() {
        // Constructed directly, as the framework does before attaching the base Context.
        VazieVpnService()
    }
}
