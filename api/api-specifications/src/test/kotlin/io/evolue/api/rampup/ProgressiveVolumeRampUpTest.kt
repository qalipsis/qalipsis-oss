package io.evolue.api.rampup

import io.evolue.api.ScenarioSpecificationImplementation
import io.evolue.api.scenario
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

        val iterator = strategy.iterator(16)

        assertEquals(MinionsStartingLine(2, 50), iterator.next())
        assertEquals(MinionsStartingLine(4, 50), iterator.next())
        assertEquals(MinionsStartingLine(7, 50), iterator.next())
        assertEquals(MinionsStartingLine(3, 50), iterator.next())
        assertEquals(MinionsStartingLine(0, 50), iterator.next())
    }
}