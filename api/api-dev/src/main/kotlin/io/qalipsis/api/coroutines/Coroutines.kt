/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

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

/**
 * Returns a new [CoroutineScope] referencing the context passed as parameter or the current one if omitted.
 */
suspend inline fun newCoroutineScope(context: CoroutineContext? = null): CoroutineScope =
    CoroutineScope(context ?: coroutineContext)