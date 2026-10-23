package app.vazie.vpn.data.update

import app.vazie.vpn.update.api.AppVersionPolicy
import app.vazie.vpn.update.api.StoredUpdateState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** What survives a restart — and that a damaged or hand-edited file can neither block nor redirect anybody. */
class FileAppUpdateStoreTest {

    private val directory = File.createTempFile("update-store", "").apply { delete(); mkdirs() }
    private val file = File(directory, "state.json")
    private val policy = AppVersionPolicy(42, "1.4.0", 39, "https://vazie.app/vpn", mapOf("ru" to "Обновите"))

    @Test
    fun `nothing stored reads as nothing remembered`() {
        assertEquals(StoredUpdateState(), FileAppUpdateStore(file).read())
    }

    @Test
    fun `a policy and a postponement survive a restart`() = runBlocking<Unit> {
        val state = StoredUpdateState(policy, postponedVersionCode = 42, postponedAtMillis = 1_234_567L)
        FileAppUpdateStore(file).write(state)

        assertEquals(state, FileAppUpdateStore(file).read())
    }

    @Test
    fun `a torn or foreign file remembers nothing`() {
        for (content in listOf("", "{", "not json", "[]", """{"policy":"nope"}""")) {
            file.writeText(content)
            assertEquals(StoredUpdateState(), FileAppUpdateStore(file).read(), content)
        }
    }

    @Test
    fun `a stored address that is not plain https is dropped with its policy`() {
        file.writeText(
            """{"policy":{"latestVersionCode":42,"latestVersionName":"1.4.0","minimumSupportedVersionCode":39,"updateUrl":"intent://x#Intent;end"}}""",
        )

        assertNull(FileAppUpdateStore(file).read().policy, "a hand-edited file pointed the update button at an intent")
    }

    @Test
    fun `clearing the policy is written too`() = runBlocking<Unit> {
        val store = FileAppUpdateStore(file)
        store.write(StoredUpdateState(policy))
        store.write(StoredUpdateState(policy = null))

        assertNull(FileAppUpdateStore(file).read().policy)
    }
}
