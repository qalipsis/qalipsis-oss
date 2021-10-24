package io.qalipsis.core.factories.steps.join

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.InnerJoinStepSpecification
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factories.steps.singleton.NoMoreNextStepDecorator
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicMirrorStep
import io.qalipsis.core.factories.testScenario
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class InnerJoinStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<InnerJoinStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<InnerJoinStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlockingTest {
        // given
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { }
        val rightKeyExtractor: (CorrelationRecord<out Any?>) -> Any? = { }
        val spec = InnerJoinStepSpecification<Int, String>(primaryKeyExtractor, rightKeyExtractor, "other-step")
        spec.name = "my-step"
        spec.cacheTimeout = Duration.ofMillis(123)

        val otherStep: Step<*, *> = relaxedMockk {
            every { id } returns "the-other-step"
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
        converter.convert<String, Int>(creationContext as StepCreationContext<InnerJoinStepSpecification<*, *>>)

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
            assertThat(it).isInstanceOf(InnerJoinStep::class).all {
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
    internal fun `should convert spec without name to a NoMoreNextStepDecorator`() = runBlockingTest {
        // given
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { }
        val rightKeyExtractor: (CorrelationRecord<out Any?>) -> Any? = { }
        val spec = InnerJoinStepSpecification<Int, String>(primaryKeyExtractor, rightKeyExtractor, "other-step")
        spec.cacheTimeout = Duration.ofMillis(123)

        val otherDecoratedStep: Step<*, *> = relaxedMockk {
            every { id } returns "the-other-step"
        }
        val otherStep: NoMoreNextStepDecorator<*, *> = relaxedMockk {
            every { id } returns "the-other-step"
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
        converter.convert<String, Int>(creationContext as StepCreationContext<InnerJoinStepSpecification<*, *>>)

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
            assertThat(it).isInstanceOf(InnerJoinStep::class).all {
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
