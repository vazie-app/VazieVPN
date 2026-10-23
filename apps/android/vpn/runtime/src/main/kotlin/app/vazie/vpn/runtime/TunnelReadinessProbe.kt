package app.vazie.vpn.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/** Proves the tunnel is usable, in the three ways it can separately fail — and gives up on each of them on a
 * schedule it controls. */
internal class TunnelReadinessProbe(
    private val operations: ProbeOperations = AndroidProbeOperations(),
    private val stageBudgetMillis: Long = STAGE_BUDGET_MILLIS,
    private val totalBudgetMillis: Long = TOTAL_BUDGET_MILLIS,
    workers: CoroutineDispatcher = Dispatchers.IO,
) : ReadinessProbe {

    /** Deliberately not a child of whoever calls [probe]. A worker parented to the caller would make the
     * caller wait for it, which is the exact mistake being fixed. */
    private val abandonable = CoroutineScope(SupervisorJob() + workers)

    override suspend fun probe(): TunnelReadiness {
        var remaining = totalBudgetMillis
        for (stage in ReadinessStage.entries) {
            VpnTrace.record(stage.event(), VpnTraceDetail.Outcome(VpnTraceOutcome.REQUESTED))
            val budget = minOf(stageBudgetMillis, remaining)
            val started = System.nanoTime()
            val passed = if (budget <= 0) null else runBounded(budget) { operations.run(stage) }
            remaining -= (System.nanoTime() - started) / NANOS_PER_MILLI

            val outcome = when (passed) {
                true -> VpnTraceOutcome.PASSED
                false -> VpnTraceOutcome.FAILED
                // The stage never answered. A different fact from "it answered no", and the one
                // that used to end the connection attempt in silence.
                null -> VpnTraceOutcome.TIMED_OUT
            }
            VpnTrace.record(stage.event(), VpnTraceDetail.Outcome(outcome))
            if (passed != true) return TunnelReadiness.NotReady(stage)
        }
        return TunnelReadiness.Ready
    }

    /** Runs [block] somewhere else and waits [budgetMillis] for an answer. */
    private suspend fun runBounded(budgetMillis: Long, block: () -> Boolean): Boolean? {
        val answer = CompletableDeferred<Boolean>()
        val worker = abandonable.launch {
            val result = runCatching { runInterruptible { block() } }.getOrDefault(false)
            answer.complete(result)
        }
        return try {
            withTimeoutOrNull(budgetMillis) { answer.await() }
        } finally {
            // Interrupts a worker still blocked in a socket call and marks the rest abandoned. It
            // cannot free a thread inside the platform resolver; see the class comment.
            worker.cancel()
        }
    }

    private fun ProbeOperations.run(stage: ReadinessStage): Boolean = when (stage) {
        ReadinessStage.TLS -> tlsToAddress()
        ReadinessStage.DNS -> resolveName()
    }

    private fun ReadinessStage.event(): VpnTraceEvent = when (this) {
        ReadinessStage.TLS -> VpnTraceEvent.TLS_PROBE
        ReadinessStage.DNS -> VpnTraceEvent.DNS_PROBE
    }

    private companion object {
        /** A per-stage budget and a shared total: either alone lets `Connecting` last too long. */
        const val STAGE_BUDGET_MILLIS = 10_000L
        const val TOTAL_BUDGET_MILLIS = 20_000L

        const val NANOS_PER_MILLI = 1_000_000L
    }
}
