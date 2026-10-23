package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieFaq
import app.vazie.vpn.core.model.VazieFaqItem
import app.vazie.vpn.core.model.VazieFaqLink
import app.vazie.vpn.core.model.VazieFaqText
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** The FAQ screen's questions: only the backend's, cached on disk, and asked again freely while nothing is cached. */
class FaqRepositoryTest {

    private val directory = Files.createTempDirectory("faq").toFile()
    private val file = File(directory, "faq.json")
    private var now = 0L
    private var answer: VazieFaq? = FAQ
    private var asked = 0

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `nothing until the backend answers, then its questions, kept for the next cold start`() = runTest {
        val repository = repository()
        assertNull(repository.faq.value)
        assertTrue(repository.loading.value, "before the first answer nothing is known to be missing")

        repository.refresh()
        assertEquals(FAQ, repository.faq.value)
        assertFalse(repository.loading.value)

        answer = null
        val restarted = repository()
        restarted.refresh()
        assertEquals(FAQ, restarted.faq.value, "offline after a restart shows the stored list, lines and links included")
    }

    @Test
    fun `with nothing cached every try asks, once cached at most once per interval`() = runTest {
        answer = null
        val repository = repository()
        repository.refresh()
        repository.refresh()
        assertEquals(2, asked, "try again must ask again while there is nothing to show")
        assertNull(repository.faq.value)
        assertFalse(repository.loading.value)

        answer = FAQ
        repository.refresh()
        repository.refresh()
        assertEquals(3, asked)

        now += INTERVAL
        answer = VazieFaq(emptyList())
        repository.refresh()
        assertEquals(VazieFaq(emptyList()), repository.faq.value, "every question switched off is believed")
    }

    private fun kotlinx.coroutines.test.TestScope.repository() = FaqRepository(
        source = { asked++; answer },
        file = file,
        nowMillis = { now },
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private companion object {
        const val INTERVAL = 600_000L
        val FAQ = VazieFaq(
            listOf(
                VazieFaqItem("what", VazieFaqText("Что?", "What?"), VazieFaqText("Первая\nвторая", "One\ntwo")),
                VazieFaqItem("refund", VazieFaqText("Возврат?"), VazieFaqText("Пишите."), VazieFaqLink("/legal/refunds", VazieFaqText("Возвраты"))),
            ),
        )
    }
}
