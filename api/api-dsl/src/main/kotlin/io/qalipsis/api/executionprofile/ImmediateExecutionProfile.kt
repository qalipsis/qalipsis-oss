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

package io.qalipsis.api.executionprofile

import io.qalipsis.api.scenario.ExecutionProfileSpecification

/**
 * Execution profile strategy to start all the minions at once.
 *
 * @author Eric Jessé
 */
class ImmediateExecutionProfile : ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        ImmediateExecutionProfileIterator(totalMinionsCount)

    inner class ImmediateExecutionProfileIterator(private val totalMinionsCount: Int) : ExecutionProfileIterator {

        var hasNext = true

        override fun next(): MinionsStartingLine {
            hasNext = false
            return MinionsStartingLine(totalMinionsCount, 0)
        }

        override fun hasNext(): Boolean {
            return hasNext
        }
    }
}

/**
 * Start all the minions at once.
 */
fun ExecutionProfileSpecification.immediate() {
    strategy(ImmediateExecutionProfile())
}
