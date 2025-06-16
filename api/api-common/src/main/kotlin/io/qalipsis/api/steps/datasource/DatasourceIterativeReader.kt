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

package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepStartStopContext

/**
 * Reads objects from a datasource in an iterative way.
 *
 * @param R the type of the object read and returned
 *
 * @author Eric Jessé
 */
interface DatasourceIterativeReader<R> {

    fun start(context: StepStartStopContext) = Unit

    fun stop(context: StepStartStopContext) = Unit

    /**
     * Returns `true` if the iteration has more elements.
     */
    suspend operator fun hasNext(): Boolean

    /**
     * Returns the next element in the iteration.
     */
    suspend operator fun next(): R
}
