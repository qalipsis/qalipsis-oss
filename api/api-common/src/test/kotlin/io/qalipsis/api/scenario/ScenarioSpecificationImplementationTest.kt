/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.api.scenario

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.io.Closeable
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

    @Test
    fun `should add then override and get an extension`() {
        // given
        val scenario = ScenarioSpecificationImplementation("my-scenario")
        val extension1 = mockk<Closeable>()
        val extension2 = mockk<Closeable>()
        val extension3 = mockk<Closeable>()

        // when
        scenario.addExtension("path-1", extension1)

        // then
        assertThat(scenario.getExtension<Closeable>("path-1")).isSameInstanceAs(extension1)

        // then
        scenario.addExtension("path-2", extension2)
        assertThat(scenario.getExtension<Closeable>("path-1")).isSameInstanceAs(extension1)
        assertThat(scenario.getExtension<Closeable>("path-2")).isSameInstanceAs(extension2)

        // when
        scenario.addExtension("path-1", extension3)
        assertThat(scenario.getExtension<Closeable>("path-1")).isSameInstanceAs(extension3)
    }

    private inner class TestStep : AbstractStepSpecification<Unit, Unit, TestStep>()

    private inner class SingletonTestStep : AbstractStepSpecification<Unit, Unit, SingletonTestStep>(),
        SingletonStepSpecification {

        override val singletonConfiguration: SingletonConfiguration
            get() = relaxedMockk()

        override val isReallySingleton: Boolean = true
    }
}
