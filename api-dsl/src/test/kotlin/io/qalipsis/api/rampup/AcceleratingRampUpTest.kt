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
internal class AcceleratingRampUpTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = scenario("my-scenario") {
            rampUp {
                faster(1, 2.0, 3, 4)
            }
        } as ScenarioSpecificationImplementation

        assertEquals(AcceleratingRampUp(1, 2.0, 3, 4), scenario.rampUpStrategy)
    }

    @Test
    internal fun `should accelerate the pace until the limit`() {
        val strategy = AcceleratingRampUp(100, 2.0, 10, 4)

        val iterator = strategy.iterator(22, 1.0)

        assertEquals(MinionsStartingLine(4, 100), iterator.next())
        assertEquals(MinionsStartingLine(4, 50), iterator.next())
        assertEquals(MinionsStartingLine(4, 25), iterator.next())
        assertEquals(MinionsStartingLine(4, 12), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(2, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

    @Test
    internal fun `should accelerate the pace with factor until the limit`() {
        val strategy = AcceleratingRampUp(100, 2.0, 10, 4)

        val iterator = strategy.iterator(22, 2.0)

        assertEquals(MinionsStartingLine(4, 100), iterator.next())
        assertEquals(MinionsStartingLine(4, 25), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(2, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }
}