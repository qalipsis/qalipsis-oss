package io.qalipsis.api

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.scenario.*
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows

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
    @Timeout(3)
    internal fun `should create a scenario with a ramp-up strategy`() {
        val rampUpStrategy: RampUpStrategy = relaxedMockk { }
        val scenario = scenario("my-scenario") {
            rampUp {
                strategy(rampUpStrategy)
            }
        } as ConfiguredScenarioSpecification

        assertSame(rampUpStrategy, scenario.rampUpStrategy)
        assertSame(scenario, scenariosSpecifications["my-scenario"])
    }

    @Test
    @Timeout(3)
    internal fun `should register step with a name`() = runBlockingTest {
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val step = TestStep()
        step.scenario = scenario
        step.name = "my-name"

        scenario.add(step)

        assertTrue(scenario.rootSteps.isNotEmpty())
        assertSame(step, scenario.rootSteps[0])

        assertSame(step, scenario.find<Unit>("my-name"))
    }

    @Test
    @Timeout(3)
    internal fun `should not register step with an empty name`() {
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val step = TestStep()
        step.scenario = scenario
        step.name = ""

        scenario.add(step)

        assertTrue(scenario.rootSteps.isNotEmpty())
        assertSame(step, scenario.rootSteps[0])

        assertTrue(scenario.registeredSteps.isEmpty())
    }

    @Test
    @Timeout(3)
    internal fun `should not register step with a blank name`() {
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val step = TestStep()
        step.scenario = scenario
        step.name = "  "

        scenario.add(step)

        assertTrue(scenario.rootSteps.isNotEmpty())
        assertSame(step, scenario.rootSteps[0])

        assertTrue(scenario.registeredSteps.isEmpty())
    }

    @Test
    @Timeout(3)
    internal fun `should not accept start() twice`() {
        val scenario = ScenarioSpecificationImplementation("my-scenario")

        scenario.start()

        assertThrows<IllegalArgumentException> { scenario.start() }
    }

    @Test
    @Timeout(3)
    internal fun `should mark all dags after start under load`() {
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val step = TestStep()
        step.scenario = scenario
        step.name = ""
        val scenarioWithStart = scenario.start() as StepSpecificationRegistry

        scenarioWithStart.add(step)
        assertNotNull(step.directedAcyclicGraphId)

        val singletonStep = SingletonTestStep()
        step.add(singletonStep)
        assertThat(singletonStep.directedAcyclicGraphId).all {
            isNotNull()
            isNotEqualTo(step.directedAcyclicGraphId)
        }
        assertThat(scenario.dagsUnderLoad).containsAll(
            step.directedAcyclicGraphId,
            singletonStep.directedAcyclicGraphId
        )
    }

    private inner class TestStep : AbstractStepSpecification<Unit, Unit, TestStep>()

    private inner class SingletonTestStep : AbstractStepSpecification<Unit, Unit, SingletonTestStep>(),
        SingletonStepSpecification {

        override val singletonConfiguration: SingletonConfiguration
            get() = relaxedMockk { }
    }
}
