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

/**
 * Ramp-up of minions on a scenario are defined as a sequence of starts, which are described by a [MinionsStartingLine].
 *
 * @see io.qalipsis.core.factory.orchestration.rampup.RampUpStrategy
 * @see io.qalipsis.core.factory.orchestration.rampup.RampUpStrategyIterator
 *
 * @author Eric Jessé
 */
data class MinionsStartingLine(
    /**
     * Number of minions to start on the next starting line.
     */
    val count: Int,

    /**
     * Offset of the start, related to the first start of the sequence.
     */
    val offsetMs: Long
)
