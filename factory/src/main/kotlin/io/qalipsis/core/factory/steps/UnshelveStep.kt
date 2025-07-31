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
 * Step to unshelve values from the shared state registry.
 *
 * @author Eric Jessé
 *
 * @property sharedStateRegistry the bean to keep and retrieve shared states
 * @property names the keys of all the values to fetch from the registry
 * @property delete when set to true, the values are removed from the registry after use
 */
class UnshelveStep<I>(
    id: StepName,
    private val sharedStateRegistry: SharedStateRegistry,
    private val names: List<String>,
    private val delete: Boolean
) : AbstractStep<I, Pair<I, Map<String, Any?>>>(id, null) {

    override suspend fun execute(context: StepContext<I, Pair<I, Map<String, Any?>>>) {
        val input = context.receive()
        val definitions = names.map { name -> SharedStateDefinition(context.minionId, name) }
        val values = if (delete) sharedStateRegistry.remove(definitions) else sharedStateRegistry.get(definitions)
        context.send(input to values)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}

/**
 * Step to unshelve a single value from the shared state registry.
 *
 * @author Eric Jessé
 *
 * @property sharedStateRegistry the bean to keep and retrieve shared states
 * @property shelveName the key of the value to fetch from the registry
 * @property delete when set to true, the value is removed from the registry after use
 */
class SingularUnshelveStep<I, O>(
    name: StepName,
    private val sharedStateRegistry: SharedStateRegistry,
    private val shelveName: String,
    private val delete: Boolean
) : AbstractStep<I, Pair<I, O?>>(name, null) {

    override suspend fun execute(context: StepContext<I, Pair<I, O?>>) {
        val input = context.receive()
        val definition = SharedStateDefinition(context.minionId, shelveName)
        val value = if (delete) sharedStateRegistry.remove<O>(definition) else sharedStateRegistry.get<O>(definition)
        context.send(input to value)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
