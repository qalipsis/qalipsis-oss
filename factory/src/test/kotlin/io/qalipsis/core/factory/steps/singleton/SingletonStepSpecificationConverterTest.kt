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

package io.qalipsis.core.factory.steps.singleton

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicBuilder
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicConfiguration
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicDataPushStep
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicMirrorStep
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicType
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class SingletonStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<SingletonStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var decoratedStep: Step<Int, String>

    @RelaxedMockK
    lateinit var stepSpecification: StepSpecification<Int, String, *>

    @RelaxedMockK
    lateinit var dataTransferTopic: Topic<String>

    @RelaxedMockK
    lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    lateinit var runner: Runner

    @AfterEach
    fun tearDown() {
        unmockkObject(TopicBuilder)
    }

    @Test
    internal fun `should have order 100`() {
        // then + when
        assertEquals(100, converter.order)
    }

    @Test
    internal fun `should decorate step singleton specifications`() = runBlockingTest {
        val topicConfiguration = slot<TopicConfiguration>()
        mockkObject(TopicBuilder)
        every { TopicBuilder.build<String>(capture(topicConfiguration)) } returns dataTransferTopic

        val nextSpec1: StepSpecification<String, *, *> = relaxedMockk()
        val nextSpec2: StepSpecification<String, *, *> = relaxedMockk()
        val singletonSpec = TestSingletonSpecification()
        singletonSpec.nextSteps.add(nextSpec1)
        singletonSpec.nextSteps.add(nextSpec2)
        singletonSpec.singletonConfiguration.type = SingletonType.LOOP
        singletonSpec.singletonConfiguration.bufferSize = 6543
        singletonSpec.singletonConfiguration.idleTimeout = Duration.ofSeconds(1265)

        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, singletonSpec)
        creationContext.createdStep(decoratedStep)

        // when
        converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(NoMoreNextStepDecorator::class)
            prop("decorated").isSameAs(decoratedStep)
        }

        val decoratedAdditionSlot = slot<Step<*, *>>()
        verifyOnce { decoratedStep.addNext(capture(decoratedAdditionSlot)) }

        assertThat(decoratedAdditionSlot.captured).isInstanceOf(TopicMirrorStep::class).all {
            prop("topic").isSameAs(dataTransferTopic)
        }
        // Verifies the predicate always return true.
        val predicate: (context: StepContext<*, *>, value: Any?) -> Boolean =
            decoratedAdditionSlot.captured.getProperty("predicate")
        assertTrue(predicate.invoke(relaxedMockk(), relaxedMockk()))
        // Verifies the wrapper simply returns the value.
        val wrap: (context: StepContext<*, *>, value: Any?) -> Any? =
            decoratedAdditionSlot.captured.getProperty("wrap")
        val value = relaxedMockk<Any> { }
        assertSame(value, wrap.invoke(relaxedMockk(), value))

        assertThat(singletonSpec.nextSteps).each { spec ->
            spec.all {
                isInstanceOf(SingletonProxyStepSpecification::class)
                prop("topic").isSameAs(dataTransferTopic)
                transform { it.nextSteps }.hasSize(1)
            }
        }

        // The original specifications for the next steps are pushed as next of the new SingletonProxyStepSpecification.
        assertThat(singletonSpec.nextSteps[0]).all {
            prop("next").isSameAs(nextSpec1)
            transform { it.nextSteps[0] }.isSameAs(nextSpec1)
        }
        assertThat(singletonSpec.nextSteps[1]).all {
            prop("next").isSameAs(nextSpec2)
            transform { it.nextSteps[0] }.isSameAs(nextSpec2)
        }
        assertThat(topicConfiguration.captured).all {
            prop("type").isEqualTo(TopicType.LOOP)
            prop("bufferSize").isEqualTo(6543)
            prop("idleTimeout").isEqualTo(Duration.ofSeconds(1265))
        }
        verifyOnce { TopicBuilder.build<String>(refEq(topicConfiguration.captured)) }
    }


    @Test
    internal fun `should not decorate non singleton step`() = runBlockingTest {
        // given
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)

        // when
        converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)

        // then
        assertThat(creationContext.createdStep!!).isSameAs(decoratedStep)
    }


    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<SingletonProxyStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec into a SingletonProxyStep`() = runBlockingTest {
        // given
        mockkObject(TopicBuilder)
        every { TopicBuilder.build<String>(any()) } returns dataTransferTopic
        val nextStep: StepSpecification<String, *, *> = relaxedMockk()
        val spec = SingletonProxyStepSpecification(
            "my-singleton",
            nextStep,
            dataTransferTopic
        )
        spec.name = "my-proxy"
        every { directedAcyclicGraph.isUnderLoad } returns true
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<SingletonProxyStepSpecification<*>>)

        // then
        creationContext.createdStep!!.let { step ->
            assertNotNull(step.name)
            assertThat(step).isInstanceOf(SingletonProxyStep::class).all {
                typedProp<Topic<String>>("topic").isSameAs(dataTransferTopic)
            }
        }
    }

    @Test
    internal fun `should convert spec into a TopicDataPushStep`() = runBlockingTest {
        // given
        mockkObject(TopicBuilder)
        every { TopicBuilder.build<String>(any()) } returns dataTransferTopic
        val nextStep: StepSpecification<String, *, *> = relaxedMockk()
        val spec = SingletonProxyStepSpecification(
            "my-singleton",
            nextStep,
            dataTransferTopic
        )
        spec.name = "my-proxy"
        every { directedAcyclicGraph.isUnderLoad } returns false
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<SingletonProxyStepSpecification<*>>)

        // then
        creationContext.createdStep!!.let { step ->
            assertNotNull(step.name)
            assertThat(step).isInstanceOf(TopicDataPushStep::class).all {
                prop("parentStepName").isEqualTo("my-singleton")
                prop("topic").isSameAs(dataTransferTopic)
                prop("coroutineScope").isSameAs(campaignCoroutineScope)
            }
        }
    }

    inner class TestSingletonSpecification(
        override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)
    ) : AbstractStepSpecification<Int, Int, TestSingletonSpecification>(), SingletonStepSpecification

}
