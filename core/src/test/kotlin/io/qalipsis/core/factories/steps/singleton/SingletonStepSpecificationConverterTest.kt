package io.qalipsis.core.factories.steps.singleton

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.core.factories.orchestration.Runner
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicBuilder
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicConfiguration
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicDataPushStep
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicMirrorStep
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicType
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import io.qalipsis.test.utils.getProperty
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
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

    @Test
    internal fun `should have order 100`() {
        // then + when
        assertEquals(100, converter.order)
    }

    @Test
    internal fun `should decorate step singleton specifications`() {
        mockkObject(TopicBuilder)
        val topicConfiguration = slot<TopicConfiguration>()
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
        runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

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
    internal fun `should not decorate non singleton step`() {
        // given
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, stepSpecification)
        creationContext.createdStep(decoratedStep)
        // when
        runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

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
    internal fun `should convert spec without name into a SingletonProxyStep`() {
        // given
        every { TopicBuilder.build<String>(any()) } returns dataTransferTopic
        val nextStep: StepSpecification<String, *, *> = relaxedMockk()
        val spec = SingletonProxyStepSpecification(
                "my-singleton",
                nextStep,
                dataTransferTopic
        )
        every { directedAcyclicGraph.isUnderLoad } returns true
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<SingletonProxyStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let { step ->
            assertNotNull(step.id)
            assertThat(step).isInstanceOf(SingletonProxyStep::class).all {
                typedProp<Topic<String>>("topic").isSameAs(dataTransferTopic)
            }
        }
    }

    @Test
    internal fun `should convert spec without name into a TopicDataPushStep`() {
        // given
        every { TopicBuilder.build<String>(any()) } returns dataTransferTopic
        val nextStep: StepSpecification<String, *, *> = relaxedMockk()
        val spec = SingletonProxyStepSpecification(
                "my-singleton",
                nextStep,
                dataTransferTopic
        )
        every { directedAcyclicGraph.isUnderLoad } returns false
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<SingletonProxyStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let { step ->
            assertNotNull(step.id)
            assertThat(step).isInstanceOf(TopicDataPushStep::class).all {
                typedProp<Topic<String>>("parentStepId").isEqualTo("my-singleton")
                typedProp<Topic<String>>("topic").isSameAs(dataTransferTopic)
            }
        }
    }

    companion object {

        @BeforeAll
        @JvmStatic
        fun setUpAll() {
            mockkObject(TopicBuilder)
        }

        @AfterAll
        @JvmStatic
        fun tearDownAll() {
            unmockkObject(TopicBuilder)
        }

    }

    inner class TestSingletonSpecification(
            override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)
    ) : AbstractStepSpecification<Int, Int, TestSingletonSpecification>(), SingletonStepSpecification

}
