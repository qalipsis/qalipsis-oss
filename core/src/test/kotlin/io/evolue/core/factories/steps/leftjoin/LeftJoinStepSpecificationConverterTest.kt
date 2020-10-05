package io.evolue.core.factories.steps.leftjoin

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.messaging.Topic
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.orchestration.Scenario
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.steps.LeftJoinStepSpecification
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factories.steps.singleton.NoMoreNextStepDecorator
import io.evolue.core.factories.steps.topicmirror.TopicMirrorStep
import io.evolue.test.assertk.prop
import io.evolue.test.assertk.typedProp
import io.evolue.test.mockk.coVerifyNever
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import io.evolue.test.utils.getProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class LeftJoinStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<LeftJoinStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<LeftJoinStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { }
        val rightKeyExtractor: (CorrelationRecord<out Any?>) -> Any? = { }
        val spec = LeftJoinStepSpecification<Int, String>(primaryKeyExtractor, rightKeyExtractor, "other-step")
        spec.name = "my-step"
        spec.cacheTimeout = Duration.ofMillis(123)

        val otherStep: Step<*, *> = relaxedMockk {
            every { id } returns "the-other-step"
        }
        val scenarioSpec: MutableScenarioSpecification = relaxedMockk {
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
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<LeftJoinStepSpecification<*, *>>)
        }

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
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(LeftJoinStep::class)
                prop("leftKeyExtractor").isSameAs(primaryKeyExtractor)
                typedProp<Collection<RightCorrelation<*>>>("rightCorrelations").all {
                    hasSize(1)
                    transform { correlations -> correlations.first() }.all {
                        prop("sourceStepId").isEqualTo("the-other-step")
                        prop("topic").isSameAs(topic)
                        prop("keyExtractor").isSameAs(rightKeyExtractor)
                    }
                }
            }
        }
    }

    @Test
    internal fun `should convert spec without name to a NoMoreNextStepDecorator`() {
        // given
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { }
        val rightKeyExtractor: (CorrelationRecord<out Any?>) -> Any? = { }
        val spec = LeftJoinStepSpecification<Int, String>(primaryKeyExtractor, rightKeyExtractor, "other-step")
        spec.cacheTimeout = Duration.ofMillis(123)

        val otherDecoratedStep: Step<*, *> = relaxedMockk {
            every { id } returns "the-other-step"
        }
        val otherStep: NoMoreNextStepDecorator<*, *> = relaxedMockk {
            every { id } returns "the-other-step"
            every { decorated } returns otherDecoratedStep
        }
        val scenarioSpec: MutableScenarioSpecification = relaxedMockk {
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
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<LeftJoinStepSpecification<*, *>>)
        }

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
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(LeftJoinStep::class)
                prop("leftKeyExtractor").isSameAs(primaryKeyExtractor)
                typedProp<Collection<RightCorrelation<*>>>("rightCorrelations").all {
                    hasSize(1)
                    transform { correlations -> correlations.first() }.all {
                        prop("sourceStepId").isEqualTo("the-other-step")
                        prop("topic").isSameAs(topic)
                        prop("keyExtractor").isSameAs(rightKeyExtractor)
                    }
                }
            }
        }
    }
}
