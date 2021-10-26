package io.qalipsis.api.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Launches a new coroutine with as [CoroutineScope.launch] does with the current MDC context.
 *
 * Other contexts might come later for tracking and debugging purpose.
 *
 * See [CoroutineScope.launch] for the complete documentation.
 */
fun CoroutineScope.contextualLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job = this.launch(context + MDCContext(), start, block)

/**
 * Returns the current [CoroutineScope].
 */
suspend inline fun currentCoroutineScope(): CoroutineScope = coroutineScope { this }