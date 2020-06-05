package io.evolue.api.rampup

import io.evolue.api.scenario.ScenarioSpecificationImplementation
import io.evolue.api.scenario.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class UserDefinedRampUpTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val specification: (Long, Int, Double) -> MinionsStartingLine =
            { pastPeriodMs, totalMinionsCount, speedFactor -> MinionsStartingLine(10, 10) }

        val scenario = scenario("my-scenario") {
            rampUp {
                define(specification)
            }
        } as ScenarioSpecificationImplementation

        assertEquals(UserDefinedRampUp(specification), scenario.rampUpStrategy)
    }

    @Test
    internal fun `should provide the used defined values until there is no more minions`() {
        val specification: (Long, Int, Double) -> MinionsStartingLine =
            { pastPeriodMs, totalMinionsCount, speedFactor ->
                val period = if (pastPeriodMs == 0L) 200L else pastPeriodMs
                MinionsStartingLine(totalMinionsCount / 3, (period / speedFactor).toLong())
            }
        val strategy = UserDefinedRampUp(specification)

        val iterator = strategy.iterator(21, 2.0)

        assertEquals(MinionsStartingLine(7, 100), iterator.next())
        assertEquals(MinionsStartingLine(7, 50), iterator.next())
        assertEquals(MinionsStartingLine(7, 25), iterator.next())
        assertEquals(MinionsStartingLine(0, 12), iterator.next())
    }

}