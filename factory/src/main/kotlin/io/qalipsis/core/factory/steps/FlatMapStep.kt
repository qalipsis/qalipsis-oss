/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Step to convert a context to a collection of them.
 *
 * It is typically used when a collection is output from a step but each record of the collection
 * has to be individually processed by the next steps.
 *
 * @author Eric Jessé
 */
class FlatMapStep<I, O>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    @Suppress("UNCHECKED_CAST") private val block: ((input: I) -> Flow<O>) = { input ->
        when (input) {
            null -> emptyFlow()
            is Iterable<*> ->
                input.asFlow() as Flow<O>

            is Array<*> ->
                input.asFlow() as Flow<O>

            is Sequence<*> ->
                input.asFlow() as Flow<O>

            is Map<*, *> ->
                input.entries.map { e -> e.key to e.value }.asFlow() as Flow<O>

            else ->
                flowOf(input) as Flow<O>
        }
    }
) : AbstractStep<I, O>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.receive()
        if (context.isTail) {
            val outputValues = mutableListOf<O>()
            block(input).collect { outputValues.add(it) }
            if (outputValues.isNotEmpty()) {
                // Send all the values but the latest with tail flag disabled.
                context.isTail = false
                outputValues.take(outputValues.size - 1).forEach {
                    context.send(it)
                }
                context.isTail = true
                context.send(outputValues.last())
            }
        } else {
            block(input).collect { context.send(it) }
        }
    }

}
