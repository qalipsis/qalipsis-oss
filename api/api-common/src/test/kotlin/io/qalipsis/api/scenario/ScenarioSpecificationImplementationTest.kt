/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.scenario

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import io.mockk.every
import io.qalipsis.api.scenario.catadioptre.registeredSteps
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
    internal fun `should create a new DAG when the name is not empty but remains undefined`() {
        // given
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val previous = relaxedMockk<StepSpecification<*, *, *>> {
            every { directedAcyclicGraphName } returns "my-dag"
            every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
        }
        val next = relaxedMockk<StepSpecification<*, *, *>> {
            every { directedAcyclicGraphName } returns "_"
            every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
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
