package app.vazie.vpn.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The probe must finish even when the thing it is probing never does. */
class TunnelReadinessProbeTest {

    private val released = CountDownLatch(1)

    @AfterTest
    fun tearDown() {
        // Let any abandoned worker thread go, so the suite does not leak one per test.
        released.countDown()
    }

    @Test
    fun `a stage that never returns still ends the probe`() = runBlocking {
        val operations = BlockingOperations(blockAt = ReadinessStage.DNS, released = released)
        val probe = probe(operations)

        val readiness = withTimeout(REAL_TIME_LIMIT_MILLIS) { probe.probe() }

        assertEquals(TunnelReadiness.NotReady(ReadinessStage.DNS), readiness)
        assertEquals(1, operations.started.get(), "the blocked stage was entered more than once")
    }

    @Test
    fun `a probe whose first stage hangs never reaches the later ones`() = runBlocking {
        val operations = BlockingOperations(blockAt = ReadinessStage.TLS, released = released)
        val probe = probe(operations)

        val readiness = withTimeout(REAL_TIME_LIMIT_MILLIS) { probe.probe() }

        assertEquals(TunnelReadiness.NotReady(ReadinessStage.TLS), readiness)
        assertTrue(operations.reached.isEmpty(), "a later stage ran after an unanswered one")
    }

    @Test
    fun `the total budget bounds the whole probe, not each stage separately`() = runBlocking {
        // Three stages that each hang would otherwise add up to three stage budgets. The total is
        // what keeps `Connecting` from lasting a minute.
        val operations = BlockingOperations(blockAt = ReadinessStage.TLS, released = released)
        val probe = TunnelReadinessProbe(
            operations = operations,
            stageBudgetMillis = STAGE_BUDGET_MILLIS,
            totalBudgetMillis = STAGE_BUDGET_MILLIS,
        )

        val elapsed = System.nanoTime()
        withTimeout(REAL_TIME_LIMIT_MILLIS) { probe.probe() }
        val took = (System.nanoTime() - elapsed) / 1_000_000

        assertTrue(
            took < STAGE_BUDGET_MILLIS * 3,
            "the probe spent $took ms, which is more than the total budget allows",
        )
    }

    @Test
    fun `a tunnel that cannot carry TLS is not blamed on DNS`() = runBlocking {
        // TLS before DNS: DNS runs over HTTPS, so a TLS failure would otherwise look like a DNS failure.
        val operations = StageAnswers(failing = setOf(ReadinessStage.TLS, ReadinessStage.DNS))

        val readiness = withTimeout(REAL_TIME_LIMIT_MILLIS) { probe(operations).probe() }

        assertEquals(TunnelReadiness.NotReady(ReadinessStage.TLS), readiness)
        assertTrue(
            ReadinessStage.DNS !in operations.reached,
            "DNS was asked before TLS, so a TLS failure would be reported as a DNS one",
        )
    }

    @Test
    fun `a stage that answers no is not the same as one that never answers`() = runBlocking {
        val operations = AnsweringOperations(answer = false)

        val readiness = withTimeout(REAL_TIME_LIMIT_MILLIS) { probe(operations).probe() }

        assertIs<TunnelReadiness.NotReady>(readiness)
        assertEquals(ReadinessStage.TLS, readiness.stage)
    }

    @Test
    fun `all three stages passing is the only way to be ready`() = runBlocking {
        val operations = AnsweringOperations(answer = true)

        val readiness = withTimeout(REAL_TIME_LIMIT_MILLIS) { probe(operations).probe() }

        assertEquals(TunnelReadiness.Ready, readiness)
        assertEquals(
            ReadinessStage.entries.toSet(),
            operations.reached,
            "readiness was declared without running every stage",
        )
    }

    private fun probe(operations: ProbeOperations) = TunnelReadinessProbe(
        operations = operations,
        stageBudgetMillis = STAGE_BUDGET_MILLIS,
        totalBudgetMillis = STAGE_BUDGET_MILLIS * 4,
    )

    /** Blocks a real thread at [blockAt], the way the platform resolver did. */
    private class BlockingOperations(
        private val blockAt: ReadinessStage,
        private val released: CountDownLatch,
    ) : ProbeOperations {

        val started = AtomicInteger(0)
        val reached: MutableSet<ReadinessStage> = mutableSetOf()


        override fun tlsToAddress(): Boolean = run(ReadinessStage.TLS)

        override fun resolveName(): Boolean = run(ReadinessStage.DNS)


        private fun run(stage: ReadinessStage): Boolean {
            if (stage != blockAt) {
                reached += stage
                return true
            }
            started.incrementAndGet()
            released.await(BLOCK_FOREVER_MILLIS, TimeUnit.MILLISECONDS)
            return true
        }

        private companion object {
            /** Longer than any budget under test; the latch is released in `tearDown`. */
            const val BLOCK_FOREVER_MILLIS = 60_000L
        }
    }

    /** Answers per stage, so a test can fail exactly one of them. */
    private class StageAnswers(private val failing: Set<ReadinessStage>) : ProbeOperations {

        val reached: MutableSet<ReadinessStage> = mutableSetOf()


        override fun tlsToAddress(): Boolean = answer(ReadinessStage.TLS)

        override fun resolveName(): Boolean = answer(ReadinessStage.DNS)


        private fun answer(stage: ReadinessStage): Boolean {
            reached += stage
            return stage !in failing
        }
    }

    private class AnsweringOperations(private val answer: Boolean) : ProbeOperations {

        val reached: MutableSet<ReadinessStage> = mutableSetOf()


        override fun tlsToAddress(): Boolean = answer(ReadinessStage.TLS)

        override fun resolveName(): Boolean = answer(ReadinessStage.DNS)


        private fun answer(stage: ReadinessStage): Boolean {
            reached += stage
            return answer
        }
    }

    private companion object {
        const val STAGE_BUDGET_MILLIS = 300L

        /** If the probe is unbounded this is what fails the test instead of hanging the suite. */
        const val REAL_TIME_LIMIT_MILLIS = 30_000L
    }
}
