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

package io.qalipsis.api

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import io.mockk.every
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.scenario.ConfiguredScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecificationImplementation
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.catadioptre.registeredSteps
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.scenario.scenariosSpecifications
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

        assertTrue(scenario.registeredSteps().isEmpty())
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

        assertTrue(scenario.registeredSteps().isEmpty())
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
        assertNotNull(step.directedAcyclicGraphName)

        val singletonStep = SingletonTestStep()
        step.add(singletonStep)
        assertThat(singletonStep.directedAcyclicGraphName).all {
            isNotNull()
            isNotEqualTo(step.directedAcyclicGraphName)
        }
        assertThat(scenario.dagsUnderLoad).containsAll(
            step.directedAcyclicGraphName,
            singletonStep.directedAcyclicGraphName
        )
    }

    @Test
    internal fun `should create a new DAG when the previous step is a singleton`() {
        // given
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val previous = relaxedMockk<StepSpecification<*, *, *>>(
            moreInterfaces = arrayOf(SingletonStepSpecification::class)
        ) {
            every { directedAcyclicGraphName } returns "my-dag"
        }
        val next = relaxedMockk<StepSpecification<*, *, *>>()

        // when
        scenario.registerNext(previous, next)

        // then
        every { next setProperty "directedAcyclicGraphName" value "dag-1" }
    }

    @Test
    internal fun `should create a new DAG when the next step is a singleton`() {
        // given
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val previous = relaxedMockk<StepSpecification<*, *, *>> {
            every { directedAcyclicGraphName } returns "my-dag"
        }
        val next = relaxedMockk<StepSpecification<*, *, *>>(
            moreInterfaces = arrayOf(SingletonStepSpecification::class)
        )

        // when
        scenario.registerNext(previous, next)

        // then
        every { next setProperty "directedAcyclicGraphName" value "dag-1" }
    }

    @Test
    internal fun `should create a new DAG when the tags are not equal`() {
        // given
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val previous = relaxedMockk<StepSpecification<*, *, *>> {
            every { directedAcyclicGraphName } returns "my-dag"
            every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
        }
        val next = relaxedMockk<StepSpecification<*, *, *>> {
            every { tags } returns mapOf("key3" to "value3", "key4" to "value4")
        }

        // when
        scenario.registerNext(previous, next)

        // then
        every { next setProperty "directedAcyclicGraphName" value "dag-1" }
    }

    @Test
    internal fun `should not create a new DAG when the tags are equal and there is no singleton`() {
        // given
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val previous = relaxedMockk<StepSpecification<*, *, *>> {
            every { directedAcyclicGraphName } returns "my-dag"
            every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
        }
        val next = relaxedMockk<StepSpecification<*, *, *>> {
            every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
        }

        // when
        scenario.registerNext(previous, next)

        // then
        every { next setProperty "directedAcyclicGraphName" value "my-dag" }
    }

    private inner class TestStep : AbstractStepSpecification<Unit, Unit, TestStep>()

    private inner class SingletonTestStep : AbstractStepSpecification<Unit, Unit, SingletonTestStep>(),
        SingletonStepSpecification {

        override val singletonConfiguration: SingletonConfiguration
            get() = relaxedMockk()

        override val isReallySingleton: Boolean = true
    }
}
