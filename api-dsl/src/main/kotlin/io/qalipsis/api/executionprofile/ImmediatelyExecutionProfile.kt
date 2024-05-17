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

package io.qalipsis.api.executionprofile

import io.qalipsis.api.scenario.ExecutionProfileSpecification

/**
 * Execution profile strategy to start all the minions at once.
 *
 * @author Eric Jessé
 */
class ImmediatelyExecutionProfile : ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        ImmediatelyExecutionProfileIterator(totalMinionsCount)

    inner class ImmediatelyExecutionProfileIterator(private val totalMinionsCount: Int) : ExecutionProfileIterator {

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
fun ExecutionProfileSpecification.immediately() {
    strategy(ImmediatelyExecutionProfile())
}
