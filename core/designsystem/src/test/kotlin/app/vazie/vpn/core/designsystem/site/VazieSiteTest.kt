package app.vazie.vpn.core.designsystem.site

import kotlin.test.Test
import kotlin.test.assertEquals

class VazieSiteTest {

    @Test
    fun `a page is the site's address and its path`() {
        assertEquals("https://vazie.app/vpn/faq", VazieSite("https://vazie.app").page("/vpn/faq"))
    }

    @Test
    fun `a local site keeps its port`() {
        assertEquals("http://localhost:3010/vpn/faq", VazieSite("http://localhost:3010").page("/vpn/faq"))
    }
}
