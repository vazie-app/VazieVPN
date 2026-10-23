package app.vazie.vpn.feature.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Keeps a `WhileSubscribed` state flow alive for the length of a test. */
internal fun <T> Flow<T>.collectIn(scope: CoroutineScope): Job = scope.launch { collect { } }
