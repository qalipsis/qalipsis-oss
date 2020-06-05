package io.evolue.api.rampup

import io.evolue.api.scenario.ScenarioSpecificationImplementation
import io.evolue.api.scenario.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class TimeFrameRampUpTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = scenario("my-scenario") {
            rampUp {
                timeframe(1, 20)
            }
        } as ScenarioSpecificationImplementation

        assertEquals(TimeFrameRampUp(1, 20), scenario.rampUpStrategy)
    }

    @Test
    internal fun `should provide adaptive count at constant pace`() {
        val strategy = TimeFrameRampUp(10, 35)

        val iterator = strategy.iterator(10, 1.0)

        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(4, 10), iterator.next())
        assertEquals(MinionsStartingLine(2, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

    @Test
    internal fun `should provide adaptive count with factor at constant pace`() {
        val strategy = TimeFrameRampUp(10, 35)

        val iterator = strategy.iterator(10, 2.0)

        assertEquals(MinionsStartingLine(4, 5), iterator.next())
        assertEquals(MinionsStartingLine(4, 5), iterator.next())
        assertEquals(MinionsStartingLine(2, 5), iterator.next())
        assertEquals(MinionsStartingLine(0, 5), iterator.next())
    }

}