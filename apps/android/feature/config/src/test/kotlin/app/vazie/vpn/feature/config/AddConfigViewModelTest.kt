package app.vazie.vpn.feature.config

import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.ProfileOrigin
import app.vazie.vpn.api.VpnProfile
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.config.ConfigParserRegistry
import app.vazie.vpn.config.InvalidReason
import app.vazie.vpn.config.UnsupportedFeature
import app.vazie.vpn.config.subscription.SubscriptionFailure
import app.vazie.vpn.config.subscription.SubscriptionFetch
import app.vazie.vpn.config.subscription.SubscriptionReader
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The wizard over the real parser and a repository double. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddConfigViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val profiles = FakeProfileRepository()

    private val selected = FakeSelectedProfileStore()

    private val subscriptions = FakeSubscriptionSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `choosing a method opens the next step and waits for a link`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(AddConfigAction.SelectMethod(ImportMethodUi.PASTE))

        assertEquals(AddConfigEffect.OpenPreview, viewModel.effects.first())
        assertEquals(DetectionUiState.AwaitingInput, viewModel.state.value.detection)
    }

    /** Every method the type can name is one that works. */
    @Test
    fun `every import method there is opens the paste step`() = runTest(dispatcher) {
        ImportMethodUi.entries.forEach { method ->
            val viewModel = viewModel()

            viewModel.onAction(AddConfigAction.SelectMethod(method))

            assertEquals(AddConfigEffect.OpenPreview, viewModel.effects.first())
        }
    }

    /** The format phrase comes from the registry and survives entering the flow. */
    @Test
    fun `the advertised formats come from the parsers and survive a reset`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertEquals("VLESS", viewModel.state.value.supportedFormats)

        viewModel.onAction(AddConfigAction.SelectMethod(ImportMethodUi.PASTE))
        advanceUntilIdle()

        assertEquals("VLESS", viewModel.state.value.supportedFormats)
    }

    @Test
    fun `asking to paste asks the route for the clipboard`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(AddConfigAction.PasteRequested)

        assertEquals(AddConfigEffect.ReadClipboard, viewModel.effects.first())
    }

    @Test
    fun `a REALITY link becomes a preview of what it is`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.paste(ImportTestLinks.reality())

        val preview = assertIs<DetectionUiState.Recognized>(viewModel.state.value.detection).preview
        assertEquals("VLESS", preview.protocolLabel)
        assertEquals("REALITY", preview.securityLabel)
        assertEquals("TCP", preview.transportLabel)
        assertEquals("${ImportTestLinks.HOST}:443", preview.server)
        assertEquals("chrome", preview.fingerprint)
        assertEquals("xtls-rprx-vision", preview.flow)
        assertEquals(listOf("packetEncoding"), preview.keptParameters)
        assertEquals("Home relay", viewModel.state.value.name)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `a link with no name of its own is named after its host`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.paste(ImportTestLinks.reality(fragment = null))

        assertEquals(ImportTestLinks.HOST, viewModel.state.value.name)
    }

    @Test
    fun `a transport Vazie cannot run is unsupported, not invalid`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.paste(ImportTestLinks.link(query = "type=kcp"))

        val detection = assertIs<DetectionUiState.Unsupported>(viewModel.state.value.detection)
        assertEquals(UnsupportedFeature.Transport("kcp"), detection.feature)
        assertEquals("VLESS", detection.protocolLabel)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `a link Vazie cannot read is invalid, with the reason it failed`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.paste(ImportTestLinks.link(userId = "not-a-uuid"))

        val detection = assertIs<DetectionUiState.Invalid>(viewModel.state.value.detection)
        assertEquals(InvalidReason.MalformedUserId, detection.reason)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `an empty clipboard says so rather than nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.paste("")

        assertEquals(
            InvalidReason.Empty,
            assertIs<DetectionUiState.Invalid>(viewModel.state.value.detection).reason,
        )
    }

    @Test
    fun `a blank name blocks saving`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.paste(ImportTestLinks.reality())

        viewModel.onAction(AddConfigAction.NameChanged("   "))

        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `an outcome that is not recognised cannot continue`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.paste(ImportTestLinks.link(query = "type=kcp"))

        viewModel.onAction(AddConfigAction.Continue)
        advanceUntilIdle()

        assertTrue(profiles.profiles.isEmpty())
    }

    @Test
    fun `saving stores the profile and moves to the last step`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.paste(ImportTestLinks.reality())
        viewModel.onAction(AddConfigAction.NameChanged("Home relay"))

        viewModel.onAction(AddConfigAction.Save)
        advanceUntilIdle()

        val profile = assertIs<VpnProfile.Xray>(profiles.profiles.single())
        assertEquals("Home relay", profile.name)
        assertEquals(ProfileOrigin.IMPORTED_LINK, profile.origin)
        val outbound = assertIs<XrayOutbound.Vless>(profile.outbound)
        assertEquals(ImportTestLinks.USER_ID, outbound.userId.expose())
        assertEquals(listOf("packetEncoding"), outbound.unknownParameters.names)
        assertEquals(AddConfigEffect.OpenDone, viewModel.effects.first())
    }

    @Test
    fun `the stored profile id is Vazie's own and not the one from the link`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.paste(ImportTestLinks.reality())

            viewModel.onAction(AddConfigAction.Save)
            advanceUntilIdle()

            assertTrue(profiles.profiles.single().id.value != ImportTestLinks.USER_ID)
        }

    @Test
    fun `a link Vazie could not read is never stored`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.paste(ImportTestLinks.link(userId = "not-a-uuid"))

        viewModel.onAction(AddConfigAction.Save)
        advanceUntilIdle()

        assertTrue(profiles.profiles.isEmpty())
    }

    @Test
    fun `saving selects what was just imported`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.paste(ImportTestLinks.reality())
        viewModel.onAction(AddConfigAction.NameChanged("Home relay"))

        viewModel.onAction(AddConfigAction.Save)
        advanceUntilIdle()

        val stored = profiles.profiles.single()
        assertEquals(
            stored.id,
            selected.selected(),
            "the wizard added a configuration and armed a different one",
        )
    }

    @Test
    fun `the wizard's state never holds the link it read`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.paste(ImportTestLinks.reality())

        val rendered = viewModel.state.value.toString()
        assertTrue(ImportTestLinks.USER_ID !in rendered, "the user id reached the state")
        assertTrue(ImportTestLinks.PUBLIC_KEY !in rendered, "the REALITY key reached the state")
        assertTrue("packetEncoding=xudp" !in rendered, "a preserved value reached the state")
    }

    @Test
    fun `a subscription link is downloaded, and its list becomes what can be added`() = runTest(dispatcher) {
        subscriptions.answer(body(ImportTestLinks.reality("First"), ImportTestLinks.link(fragment = "Second")))
        val viewModel = viewModel()

        viewModel.paste(SUBSCRIPTION)
        assertEquals(DetectionUiState.SubscriptionLoading(SUBSCRIPTION_HOST), viewModel.state.value.detection)
        advanceUntilIdle()

        val detection = assertIs<DetectionUiState.Subscription>(viewModel.state.value.detection)
        assertEquals(SUBSCRIPTION_HOST, detection.host)
        assertEquals(listOf("First", "Second"), detection.configs.map { it.suggestedName })
        assertEquals(0, detection.skipped)
        assertEquals(listOf(SUBSCRIPTION), subscriptions.asked)
        assertTrue(viewModel.state.value.canImportSubscription)
        assertFalse(viewModel.state.value.canSave, "a list is added whole, not through the naming step")
    }

    @Test
    fun `a Base64 list is read the same, and what Vazie cannot run is counted out`() = runTest(dispatcher) {
        val list = body(ImportTestLinks.reality("First"), ImportTestLinks.link(query = "type=kcp"), "other://x")
        subscriptions.answer(Base64.getEncoder().encodeToString(list.toByteArray()))
        val viewModel = viewModel()

        viewModel.paste(SUBSCRIPTION)
        advanceUntilIdle()

        val detection = assertIs<DetectionUiState.Subscription>(viewModel.state.value.detection)
        assertEquals(listOf("First"), detection.configs.map { it.suggestedName })
        assertEquals(2, detection.skipped)
    }

    @Test
    fun `adding a subscription stores every configuration and arms the first`() = runTest(dispatcher) {
        subscriptions.answer(body(ImportTestLinks.reality("First"), ImportTestLinks.reality(fragment = null)))
        val viewModel = viewModel()
        viewModel.paste(SUBSCRIPTION)
        advanceUntilIdle()

        viewModel.onAction(AddConfigAction.ImportSubscription)
        advanceUntilIdle()

        assertEquals(listOf("First", ImportTestLinks.HOST), profiles.profiles.map { it.name })
        assertTrue(profiles.profiles.all { it.origin == ProfileOrigin.IMPORTED_LINK })
        assertEquals(profiles.profiles.first().id, selected.selected())
        assertEquals(2, viewModel.state.value.importedCount)
        assertEquals(AddConfigEffect.OpenDone, viewModel.effects.first())
    }

    @Test
    fun `a subscription cannot be added twice by pressing twice`() = runTest(dispatcher) {
        subscriptions.answer(body(ImportTestLinks.reality("First")))
        val viewModel = viewModel()
        viewModel.paste(SUBSCRIPTION)
        advanceUntilIdle()

        viewModel.onAction(AddConfigAction.ImportSubscription)
        viewModel.onAction(AddConfigAction.ImportSubscription)
        advanceUntilIdle()
        viewModel.onAction(AddConfigAction.ImportSubscription)
        advanceUntilIdle()

        assertEquals(1, profiles.profiles.size)
    }

    @Test
    fun `a list with nothing Vazie can run adds nothing and says so`() = runTest(dispatcher) {
        subscriptions.answer("<html>Not found</html>")
        val viewModel = viewModel()

        viewModel.paste(SUBSCRIPTION)
        advanceUntilIdle()
        viewModel.onAction(AddConfigAction.ImportSubscription)
        advanceUntilIdle()

        assertEquals(
            DetectionUiState.SubscriptionFailed(SUBSCRIPTION_HOST, SubscriptionFailureUi.EMPTY),
            viewModel.state.value.detection,
        )
        assertTrue(profiles.profiles.isEmpty())
    }

    @Test
    fun `a server that does not give the list is named with the reason`() = runTest(dispatcher) {
        val reasons = mapOf(
            SubscriptionFailure.UNREACHABLE to SubscriptionFailureUi.UNREACHABLE,
            SubscriptionFailure.REFUSED to SubscriptionFailureUi.REFUSED,
            SubscriptionFailure.TOO_LARGE to SubscriptionFailureUi.TOO_LARGE,
        )
        assertEquals(SubscriptionFailure.entries.toSet(), reasons.keys)

        reasons.forEach { (failure, shown) ->
            subscriptions.fail(failure)
            val viewModel = viewModel()

            viewModel.paste(SUBSCRIPTION)
            advanceUntilIdle()

            assertEquals(
                DetectionUiState.SubscriptionFailed(SUBSCRIPTION_HOST, shown),
                viewModel.state.value.detection,
            )
        }
    }

    @Test
    fun `a subscription link without TLS is refused without being asked`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.paste("http" + SUBSCRIPTION.removePrefix("https"))
        advanceUntilIdle()

        assertEquals(
            DetectionUiState.SubscriptionFailed(host = null, reason = SubscriptionFailureUi.INSECURE),
            viewModel.state.value.detection,
        )
        assertTrue(subscriptions.asked.isEmpty(), "a list of credentials was requested in the clear")
    }

    @Test
    fun `pasting something else stops waiting for the list`() = runTest(dispatcher) {
        val pending = CompletableDeferred<SubscriptionFetch>()
        subscriptions.answer(pending)
        val viewModel = viewModel()
        viewModel.paste(SUBSCRIPTION)
        advanceUntilIdle()

        viewModel.paste(ImportTestLinks.reality())
        pending.complete(SubscriptionFetch.Fetched(Secret.of(body(ImportTestLinks.reality("Late")))))
        advanceUntilIdle()

        assertIs<DetectionUiState.Recognized>(viewModel.state.value.detection)
        assertEquals("Home relay", viewModel.state.value.name)
    }

    @Test
    fun `a single link added after a subscription is announced by its name`() = runTest(dispatcher) {
        subscriptions.answer(body(ImportTestLinks.reality("First"), ImportTestLinks.reality("Second")))
        val viewModel = viewModel()
        viewModel.paste(SUBSCRIPTION)
        advanceUntilIdle()
        viewModel.onAction(AddConfigAction.ImportSubscription)
        advanceUntilIdle()

        viewModel.paste(ImportTestLinks.reality())
        viewModel.onAction(AddConfigAction.Save)
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.importedCount)
        assertEquals(3, profiles.profiles.size)
    }

    @Test
    fun `the wizard's state never holds the subscription's address or what it answered`() =
        runTest(dispatcher) {
            subscriptions.answer(body(ImportTestLinks.reality()))
            val viewModel = viewModel()

            viewModel.paste(SUBSCRIPTION)
            assertTrue(SUBSCRIPTION_TOKEN !in viewModel.state.value.toString(), "the token reached the state")
            advanceUntilIdle()

            val rendered = viewModel.state.value.toString()
            assertTrue(SUBSCRIPTION_TOKEN !in rendered, "the token reached the state")
            assertTrue(ImportTestLinks.USER_ID !in rendered, "the user id reached the state")
            assertTrue(ImportTestLinks.PUBLIC_KEY !in rendered, "the REALITY key reached the state")
        }

    private fun viewModel() = AddConfigViewModel(
        parsers = ConfigParserRegistry.default(),
        profiles = profiles,
        selected = selected,
        subscriptions = subscriptions,
        subscriptionReader = SubscriptionReader(ConfigParserRegistry.default()),
    )

    private fun body(vararg links: String): String = links.joinToString("\n")

    private fun AddConfigViewModel.paste(raw: String) = onPasted(Secret.of(raw))

    private companion object {
        const val SUBSCRIPTION_HOST = "sub.example.net"
        const val SUBSCRIPTION_TOKEN = "not-a-real-subscription-token"
        const val SUBSCRIPTION = "https://$SUBSCRIPTION_HOST/list/$SUBSCRIPTION_TOKEN"
    }
}
