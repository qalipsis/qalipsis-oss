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

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepContext
import io.qalipsis.core.factory.orchestration.TransportableContext

/**
 * Service to produce [TransportableContext] to a specified factory.
 *
 * @author Eric Jessé
 */
internal interface ContextForwarder {

    suspend fun forward(context: StepContext<*, *>, dags: Collection<DirectedAcyclicGraphName>)

    suspend fun forward(context: CompletionContext, dags: Collection<DirectedAcyclicGraphName>)

}
