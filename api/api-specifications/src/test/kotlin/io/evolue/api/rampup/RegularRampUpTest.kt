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

        val iterator = strategy.iterator(11)

        assertEquals(MinionsStartingLine(5, 10), iterator.next())
        assertEquals(MinionsStartingLine(5, 10), iterator.next())
        assertEquals(MinionsStartingLine(1, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

}