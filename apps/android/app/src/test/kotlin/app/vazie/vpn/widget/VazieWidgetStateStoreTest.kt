package app.vazie.vpn.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.vazie.vpn.core.designsystem.theme.Appearance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The store is the only thing in this stage that outlives a process, so it is the only thing that can break
 * for a user who is not running the version that broke it. */
class VazieWidgetStateStoreTest {

    @Test
    fun `a connected state survives a round trip`() {
        val original = WidgetFixtures.connected
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun `every state the widgets can be in survives a round trip`() {
        WidgetFixtures.allStates.forEach { state ->
            assertEquals(state, roundTrip(state), "lost in translation: $state")
        }
    }

    @Test
    fun `a configuration name keeps whatever the user typed`() {
        // Chips are stored under indexed keys precisely so that a name can contain any
        // character at all, including the separators a joined encoding would have had to reserve.
        val awkward = "Home · relay, \"quoted\"\nand split"
        val state = VazieWidgetState.Configured(
            selected = WidgetFixtures.selected.copy(name = awkward),
            connection = WidgetConnectionUi.Idle,
            shortcuts = listOf(WidgetFixtures.shortcuts.first().copy(name = awkward)),
        )
        val restored = assertIs<VazieWidgetState.Configured>(roundTrip(state))
        assertEquals(awkward, restored.selected.name)
        assertEquals(awkward, restored.shortcuts.single().name)
    }

    @Test
    fun `the app's appearance travels with the state, and anything unknown is the default`() {
        val preferences = mutablePreferencesOf()
        VazieWidgetStateStore.write(preferences, WidgetFixtures.idle, Appearance.MILK)
        assertEquals(Appearance.MILK, VazieWidgetStateStore.readAppearance(preferences))

        VazieWidgetStateStore.write(preferences, VazieWidgetState.NoConfiguration, Appearance.NIGHT_INDIGO)
        assertEquals(Appearance.NIGHT_INDIGO, VazieWidgetStateStore.readAppearance(preferences), "a widget with nothing to show is themed too")

        preferences[stringPreferencesKey("appearance")] = "SEPIA"
        assertEquals(Appearance.Default, VazieWidgetStateStore.readAppearance(preferences))
        assertEquals(Appearance.Default, VazieWidgetStateStore.readAppearance(mutablePreferencesOf()))
    }

    @Test
    fun `empty preferences mean there is nothing to connect to`() {
        assertEquals(
            VazieWidgetState.NoConfiguration,
            VazieWidgetStateStore.read(mutablePreferencesOf()),
        )
    }

    @Test
    fun `an unreadable connection name degrades instead of throwing`() {
        val preferences = mutablePreferencesOf()
        VazieWidgetStateStore.write(preferences, WidgetFixtures.connected)
        preferences[stringPreferencesKey("connection")] = "a-state-from-a-later-version"

        val restored = assertIs<VazieWidgetState.Configured>(
            VazieWidgetStateStore.read(preferences),
        )
        assertEquals(
            WidgetConnectionUi.Idle,
            restored.connection,
            "an unknown state should read as disconnected, never as connected",
        )
        assertEquals(WidgetFixtures.selected, restored.selected)
    }

    @Test
    fun `publishing a state clears what the previous one wrote`() {
        val preferences = mutablePreferencesOf()
        VazieWidgetStateStore.write(preferences, WidgetFixtures.connected)
        VazieWidgetStateStore.write(preferences, VazieWidgetState.NoConfiguration)

        assertEquals(
            VazieWidgetState.NoConfiguration,
            VazieWidgetStateStore.read(preferences),
            "a session read-out outlived the session it belonged to",
        )
    }

    private fun roundTrip(state: VazieWidgetState): VazieWidgetState {
        val preferences = mutablePreferencesOf()
        VazieWidgetStateStore.write(preferences, state)
        return VazieWidgetStateStore.read(preferences)
    }
}
