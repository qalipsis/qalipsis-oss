/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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