/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps.zipLast

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.ZipLastStepSpecification
import io.qalipsis.core.factory.steps.singleton.NoMoreNextStepDecorator
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicMirrorStep
import io.qalipsis.core.factory.steps.zip.RightSource
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Polina Bril
 */
@Suppress("UNCHECKED_CAST")
internal class ZipLastStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<ZipLastStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var coroutineScope: CoroutineScope

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<ZipLastStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlockingTest {
        // given
        val spec = ZipLastStepSpecification<Int, String>("other-step")
        spec.name = "my-step"

        val otherStep: Step<*, *> = relaxedMockk {
            every { name } returns "the-other-step"
        }
        val scenarioSpec: StepSpecificationRegistry = relaxedMockk {
            every { exists("other-step") } returns true
        }
        val scen: Scenario = relaxedMockk {
            coEvery { findStep("other-step") } returns (otherStep to relaxedMockk { })
        }
        val dag: DirectedAcyclicGraph = relaxedMockk {
            every { scenario } returns scen
        }
        val creationContext = StepCreationContextImpl(scenarioSpec, dag, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<ZipLastStepSpecification<*, *>>)

        // then
        val consumer = slot<Step<Int, *>>()
        coVerify {
            otherStep.addNext(capture(consumer))
        }
        assertThat(consumer.captured).isInstanceOf(TopicMirrorStep::class).all {
            prop("topic").isNotNull()

        }
        val topic: Topic<*> = consumer.captured.getProperty("topic")

        creationContext.createdStep!!.let {
            assertEquals("my-step", it.name)
            assertThat(it).isInstanceOf(ZipLastStep::class).all {
                prop("coroutineScope").isSameInstanceAs(coroutineScope)
                typedProp<Collection<RightSource<*>>>("rightSources").all {
                    hasSize(1)
                    transform { correlations -> correlations.first() }.all {
                        prop("sourceStepName").isEqualTo("the-other-step")
                        prop("topic").isSameInstanceAs(topic)
                    }
                }
            }
        }
    }

    @Test
    internal fun `should convert spec without name to a NoMoreNextStepDecorator`() = runBlockingTest {
        // given
        val spec = ZipLastStepSpecification<Int, String>("other-step")

        val otherDecoratedStep: Step<*, *> = relaxedMockk {
            every { name } returns "the-other-step"
        }
        val otherStep: NoMoreNextStepDecorator<*, *> = relaxedMockk {
            every { name } returns "the-other-step"
            every { decorated } returns otherDecoratedStep
        }
        val scenarioSpec: StepSpecificationRegistry = relaxedMockk {
            every { exists("other-step") } returns true
        }
        val scen: Scenario = relaxedMockk {
            coEvery { findStep("other-step") } returns (otherStep to relaxedMockk { })
        }
        val dag: DirectedAcyclicGraph = relaxedMockk {
            every { scenario } returns scen
        }
        val creationContext = StepCreationContextImpl(scenarioSpec, dag, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<ZipLastStepSpecification<*, *>>)

        // then
        // The consumer step is directly added to the decorated when the step after is a NoMoreNextStepDecorator.
        coVerifyNever {
            otherStep.addNext(any())
        }
        val consumer = slot<Step<Int, *>>()
        coVerify {
            otherDecoratedStep.addNext(capture(consumer))
        }
        assertThat(consumer.captured).isInstanceOf(TopicMirrorStep::class).all {
            prop("topic").isNotNull()
        }

        val topic: Topic<*> = consumer.captured.getProperty("topic")

        creationContext.createdStep!!.let {
            assertNotNull(it.name)
            assertThat(it).isInstanceOf(ZipLastStep::class).all {
                typedProp<Collection<RightSource<*>>>("rightSources").all {
                    hasSize(1)
                    transform { correlations -> correlations.first() }.all {
                        prop("sourceStepName").isEqualTo("the-other-step")
                        prop("topic").isSameInstanceAs(topic)
                    }
                }
            }
        }
    }
}