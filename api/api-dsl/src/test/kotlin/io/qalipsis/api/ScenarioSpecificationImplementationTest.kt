package io.qalipsis.api

import io.qalipsis.api.scenario.ScenarioSpecificationImplementation
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.scenario.scenariosSpecifications
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.core.factories.orchestration.rampup.RampUpStrategy
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ScenarioSpecificationImplementationTest {

    @Test
    internal fun `should create an empty scenario`() {
        val scenario = scenario("my-scenario")

        assertSame(scenario, scenariosSpecifications["my-scenario"])
    }

    @Test
    internal fun `should create a scenario with a ramp-up strategy`() {
        val rampUpStrategy: RampUpStrategy = relaxedMockk { }
        val scenario = scenario("my-scenario") {
            rampUp {
                strategy(rampUpStrategy)
            }
        } as ScenarioSpecificationImplementation

        assertSame(rampUpStrategy, scenario.rampUpStrategy)
        assertSame(scenario, scenariosSpecifications["my-scenario"])
    }

    @Test
    internal fun `should register step with a name`() {
        val scenario = scenario(
                "my-scenario") as ScenarioSpecificationImplementation
        val step = TestStep()
        step.name = "my-name"

        scenario.add(step)

        assertTrue(scenario.rootSteps.isNotEmpty())
        assertSame(step, scenario.rootSteps[0])

        assertSame(step, runBlocking { scenario.find<Unit>("my-name") })
    }

    @Test
    internal fun `should not register step with an empty name`() {
        val scenario = scenario(
                "my-scenario") as ScenarioSpecificationImplementation
        val step = TestStep()
        step.name = ""

        scenario.add(step)

        assertTrue(scenario.rootSteps.isNotEmpty())
        assertSame(step, scenario.rootSteps[0])

        assertTrue(scenario.registeredSteps.isEmpty())
    }

    @Test
    internal fun `should not register step with a null name`() {
        val scenario = scenario(
                "my-scenario") as ScenarioSpecificationImplementation
        val step = TestStep()
        step.name = null

        scenario.add(step)

        assertTrue(scenario.rootSteps.isNotEmpty())
        assertSame(step, scenario.rootSteps[0])

        assertTrue(scenario.registeredSteps.isEmpty())
    }

    inner class TestStep : AbstractStepSpecification<Unit, Unit, TestStep>()

}
