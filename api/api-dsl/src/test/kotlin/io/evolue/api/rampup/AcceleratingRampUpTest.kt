package io.evolue.api.rampup

import io.evolue.api.scenario.ScenarioSpecificationImplementation
import io.evolue.api.scenario.scenario
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