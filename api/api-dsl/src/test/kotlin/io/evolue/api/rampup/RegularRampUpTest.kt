package io.evolue.api.rampup

import io.evolue.api.ScenarioSpecificationImplementation
import io.evolue.api.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class RegularRampUpTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = scenario("my-scenario") {
            rampUp {
                regular(1, 2)
            }
        } as ScenarioSpecificationImplementation

        assertEquals(RegularRampUp(1, 2), scenario.rampUpStrategy)
    }

    @Test
    internal fun `should provide constant count at constant pace`() {
        val strategy = RegularRampUp(10, 5)

        val iterator = strategy.iterator(11, 1.0)

        assertEquals(MinionsStartingLine(5, 10), iterator.next())
        assertEquals(MinionsStartingLine(5, 10), iterator.next())
        assertEquals(MinionsStartingLine(1, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

    @Test
    internal fun `should provide constant count with factor at constant pace`() {
        val strategy = RegularRampUp(10, 5)

        val iterator = strategy.iterator(11, 2.0)

        assertEquals(MinionsStartingLine(5, 5), iterator.next())
        assertEquals(MinionsStartingLine(5, 5), iterator.next())
        assertEquals(MinionsStartingLine(1, 5), iterator.next())
        assertEquals(MinionsStartingLine(0, 5), iterator.next())
    }

}