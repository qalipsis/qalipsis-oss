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

import io.qalipsis.api.scenario.ScenarioSpecificationImplementation
import io.qalipsis.api.scenario.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class UserDefinedExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val specification: (Long, Int, Double) -> MinionsStartingLine =
            { _, _, _ -> MinionsStartingLine(10, 10) }

        val scenario = scenario("my-scenario") {
            profile {
                define(specification)
            }
        } as ScenarioSpecificationImplementation

        assertEquals(UserDefinedExecutionProfile(specification), scenario.executionProfile)
    }

    @Test
    internal fun `should provide the used defined values until there is no more minions`() {
        val specification: (Long, Int, Double) -> MinionsStartingLine =
            { pastPeriodMs, totalMinionsCount, speedFactor ->
                val period = if (pastPeriodMs == 0L) 200L else pastPeriodMs
                MinionsStartingLine(totalMinionsCount / 3, (period / speedFactor).toLong())
            }
        val executionProfile = UserDefinedExecutionProfile(specification)

        val iterator = executionProfile.iterator(21, 2.0)

        assertEquals(MinionsStartingLine(7, 100), iterator.next())
        assertEquals(MinionsStartingLine(7, 50), iterator.next())
        assertEquals(MinionsStartingLine(7, 25), iterator.next())
        assertEquals(MinionsStartingLine(0, 12), iterator.next())
    }

}