package io.evolue.api.rampup

import io.evolue.api.ScenarioSpecificationImplementation
import io.evolue.api.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class UserDefinedRampUpTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val specification: (Long, Int) -> MinionsStartingLine =
            { pastPeriodMs, totalMinionsCount -> MinionsStartingLine(10, 10) }

        val scenario = scenario("my-scenario") {
            rampUp {
                define(specification)
            }
        } as ScenarioSpecificationImplementation

        assertEquals(UserDefinedRampUp(specification), scenario.rampUpStrategy)
    }

    @Test
    internal fun `should provide the used defined values until there is no more minions`() {
        val specification: (Long, Int) -> MinionsStartingLine =
            { pastPeriodMs, totalMinionsCount -> MinionsStartingLine(10, 10) }
        val strategy = UserDefinedRampUp(specification)

        val iterator = strategy.iterator(21)

        assertEquals(MinionsStartingLine(10, 10), iterator.next())
        assertEquals(MinionsStartingLine(10, 10), iterator.next())
        assertEquals(MinionsStartingLine(1, 10), iterator.next())
        assertEquals(MinionsStartingLine(0, 10), iterator.next())
    }

}