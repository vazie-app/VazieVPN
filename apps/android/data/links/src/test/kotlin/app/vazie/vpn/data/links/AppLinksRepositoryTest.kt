package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieLink
import app.vazie.vpn.core.model.VazieLinkSection
import app.vazie.vpn.core.model.VazieLinks
import app.vazie.vpn.core.model.VazieLocalizedText
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class AppLinksRepositoryTest {

    private val directory = Files.createTempDirectory("links").toFile()
    private val file = File(directory, "links.json")
    private var now = 0L
    private var answer: VazieLinks? = LINKS
    private var asked = 0

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `nothing until the backend answers, then its links, kept for the next cold start`() = runTest {
        val repository = repository()
        assertNull(repository.links.value)

        repository.refresh()
        assertEquals(LINKS, repository.links.value)

        answer = null
        val restarted = repository()
        restarted.refresh()
        assertEquals(LINKS, restarted.links.value, "offline after a restart shows the stored list")
    }

    @Test
    fun `asked at most once per interval, and a failure keeps what is shown`() = runTest {
        val repository = repository()
        repository.refresh()
        repository.refresh()
        assertEquals(1, asked)

        now += INTERVAL
        answer = null
        repository.refresh()
        assertEquals(2, asked)
        assertEquals(LINKS, repository.links.value)

        now += INTERVAL
        val changed = VazieLinks(LINKS.links.take(1))
        answer = changed
        repository.refresh()
        assertEquals(changed, repository.links.value)
    }

    @Test
    fun `a stored file is checked like an answer, so an edit opens nothing new`() = runTest {
        file.parentFile.mkdirs()
        file.writeText("""{"links":[{"key":"x","section":"AUTHOR","url":"intent://x#Intent;end","title":{"ru":"a","en":"b"}}]}""")
        answer = null
        val repository = repository()
        repository.refresh()
        assertNull(repository.links.value)

        file.writeText("not json")
        repository().apply { refresh() }.also { assertNull(it.links.value) }
    }

    private fun kotlinx.coroutines.test.TestScope.repository() = AppLinksRepository(
        source = { asked++; answer },
        file = file,
        nowMillis = { now },
        minIntervalMillis = INTERVAL,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private companion object {
        const val INTERVAL = 600_000L
        val LINKS = VazieLinks(
            listOf(
                VazieLink("behance", VazieLinkSection.PROJECT, "https://www.behance.net/gallery/1", VazieLocalizedText("Поддержать", "Support"), VazieLocalizedText("Кейс", "The case")),
                VazieLink("boosty", VazieLinkSection.SUPPORT, "https://boosty.to/vazie", VazieLocalizedText("Кофе", "Coffee"), action = VazieLocalizedText("Открыть", "Open")),
            ),
        )
    }
}
