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

/**
 * Step to transform a record.
 *
 * @author Eric Jessé
 */
class MapWithContextStep<I, O>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    @Suppress("UNCHECKED_CAST") private val block: ((context: StepContext<I, O>, input: I) -> O) = { _, value -> value as O }
) : AbstractStep<I, O>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, O>) {
        val input = context.receive()
        val output = block(context, input)
        context.send(output)
    }

}
