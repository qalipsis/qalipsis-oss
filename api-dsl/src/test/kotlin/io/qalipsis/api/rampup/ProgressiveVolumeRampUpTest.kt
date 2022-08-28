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

package io.qalipsis.api.rampup

import io.qalipsis.api.scenario.ScenarioSpecificationImplementation
import io.qalipsis.api.scenario.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ProgressiveVolumeRampUpTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = scenario("my-scenario") {
            rampUp {
                more(1, 2, 3.0, 4)
            }
        } as ScenarioSpecificationImplementation

        assertEquals(ProgressiveVolumeRampUp(1, 2, 3.0, 4), scenario.rampUpStrategy)
    }

    @Test
    internal fun `should increase the volume until the limit`() {
        val strategy = ProgressiveVolumeRampUp(50, 2, 2.0, 7)

        val iterator = strategy.iterator(16, 1.0)

        assertEquals(MinionsStartingLine(2, 50), iterator.next())
        assertEquals(MinionsStartingLine(4, 50), iterator.next())
        assertEquals(MinionsStartingLine(7, 50), iterator.next())
        assertEquals(MinionsStartingLine(3, 50), iterator.next())
        assertEquals(MinionsStartingLine(0, 50), iterator.next())
    }

    @Test
    internal fun `should increase the volume with factor until the limit`() {
        val strategy = ProgressiveVolumeRampUp(50, 2, 2.0, 25)

        val iterator = strategy.iterator(50, 2.0)

        assertEquals(MinionsStartingLine(2, 50), iterator.next())
        assertEquals(MinionsStartingLine(8, 50), iterator.next())
        assertEquals(MinionsStartingLine(25, 50), iterator.next())
        assertEquals(MinionsStartingLine(15, 50), iterator.next())
        assertEquals(MinionsStartingLine(0, 50), iterator.next())
    }
}