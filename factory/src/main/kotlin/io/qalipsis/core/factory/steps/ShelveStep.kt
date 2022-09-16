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
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.states.SharedStateDefinition
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to shelve values into the shared state registry.
 *
 * @author Eric Jessé
 *
 * @property sharedStateRegistry the bean to keep and retrieve shared states
 * @property specification the closure generating a map of values to push to the shared registry from the context input
 */
internal class ShelveStep<I>(
    id: StepName,
    private val sharedStateRegistry: SharedStateRegistry,
    private val specification: (input: I) -> Map<String, Any?>
) : AbstractStep<I, I>(id, null) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        sharedStateRegistry.set(
            specification(input).mapKeys { entry -> SharedStateDefinition(context.minionId, entry.key) })
        context.send(input)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
