package io.evolue.core.factory.steps.singleton

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.messaging.Topic
import io.evolue.api.steps.AbstractStepSpecification
import io.evolue.api.steps.SingletonConfiguration
import io.evolue.api.steps.SingletonStepSpecification
import io.evolue.api.steps.SingletonType
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.StepSpecification
import io.evolue.core.factory.steps.decorators.OutputTopicStepDecorator
import io.evolue.core.factory.steps.decorators.TopicBuilder
import io.evolue.core.factory.steps.decorators.TopicConfiguration
import io.evolue.core.factory.steps.decorators.TopicType
import io.evolue.test.assertk.prop
import io.evolue.test.assertk.typedProp
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class SingletonStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<SingletonStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var decoratedStep: Step<Int, String>

    @RelaxedMockK
    lateinit var stepSpecification: StepSpecification<Int, String, *>

    @RelaxedMockK
    lateinit var buildTopic: Topic<String>

    @BeforeAll
    internal fun setUpAll() {
        mockkObject(TopicBuilder)
    }

    @AfterAll
    internal fun tearDownAll() {
        unmockkObject(TopicBuilder)
    }

    @Test
    internal fun `should have order 100`() {
        // then + when
        assertEquals(100, converter.order)
    }

    @Test
    internal fun `should decorate step singleton specifications`() {
        mockkObject(TopicBuilder)
        val topicConfiguration = slot<TopicConfiguration>()
        every { TopicBuilder.build<String>(capture(topicConfiguration)) } returns buildTopic

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
            isInstanceOf(OutputTopicStepDecorator::class)
            prop("decorated").isSameAs(decoratedStep)
        }

        assertThat(singletonSpec.nextSteps).each { spec ->
            spec.all {
                isInstanceOf(SingletonProxyStepSpecification::class)
                prop("topic").isSameAs(buildTopic)
                transform { it.nextSteps }.hasSize(1)
            }
        }
        // The "decorated" original specification is pushed as next of the new SingletonProxyStepSpecification.
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
        Assertions.assertTrue(converter.support(relaxedMockk<SingletonProxyStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec without name`() {
        // given
        every { TopicBuilder.build<String>(any()) } returns buildTopic
        val nextStep: StepSpecification<String, *, *> = relaxedMockk()
        val spec = SingletonProxyStepSpecification(
                nextStep,
                buildTopic
        )
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<SingletonProxyStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let { step ->
            assertNotNull(step.id)
            assertThat(step).all {
                isInstanceOf(SingletonProxyStep::class)
                typedProp<Topic<String>>("topic").isSameAs(buildTopic)
            }
        }
    }

    inner class TestSingletonSpecification(
            override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)
    ) : AbstractStepSpecification<Int, Int, TestSingletonSpecification>(),
        SingletonStepSpecification<Int, Int, TestSingletonSpecification>

}
