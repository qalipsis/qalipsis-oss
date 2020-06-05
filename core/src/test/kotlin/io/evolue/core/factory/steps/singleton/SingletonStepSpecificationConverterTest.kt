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
import io.evolue.api.steps.SingletonStepSpecification
import io.evolue.api.steps.SingletonType
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.api.steps.StepSpecification
import io.evolue.test.assertk.prop
import io.evolue.test.assertk.typedProp
import io.evolue.test.mockk.relaxedMockk
import io.mockk.every
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class SingletonStepSpecificationConverterTest {

    @Test
    internal fun `should have order 100`() {
        // given
        val converter = SingletonStepSpecificationConverter()

        // then + when
        assertEquals(100, converter.order)
    }

    @Test
    internal fun `should decorate step singleton specifications`() {
        // given
        val converter = SingletonStepSpecificationConverter()

        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val nextSpec1: StepSpecification<String, *, *> = relaxedMockk()
        val nextSpec2: StepSpecification<String, *, *> = relaxedMockk()
        val singletonSpec = TestSingletonSpecification(
            SingletonType.BROADCAST,
            123,
            Duration.ofMillis(456),
            true
        )
        singletonSpec.nextSteps.add(nextSpec1)
        singletonSpec.nextSteps.add(nextSpec2)

        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), singletonSpec)
        creationContext.createdStep(mockedCreatedStep)

        // when
        runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(SingletonOutputDecorator::class)
            prop("decorated").isSameAs(mockedCreatedStep)
        }

        assertThat(singletonSpec.nextSteps).each {
            it.all {
                isInstanceOf(SingletonProxyStepSpecification::class)
                prop("singletonOutputDecorator").isSameAs(creationContext.createdStep)
                prop("singletonType").isEqualTo(SingletonType.BROADCAST)
                prop("bufferSize").isEqualTo(123)
                prop("idleTimeout").isEqualTo(Duration.ofMillis(456))
                prop("fromBeginning").isEqualTo(true)
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
    }


    @Test
    internal fun `should not decorate non singleton step`() {
        // given
        val converter = SingletonStepSpecificationConverter()
        val mockedCreatedStep: Step<Int, String> = relaxedMockk()
        val creationContext =
            StepCreationContextImpl(relaxedMockk(), relaxedMockk(), relaxedMockk<StepSpecification<Int, String, *>>())
        creationContext.createdStep(mockedCreatedStep)
        // when
        runBlocking {
            converter.decorate(creationContext as StepCreationContext<StepSpecification<*, *, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).isSameAs(mockedCreatedStep)
    }

    inner class TestSingletonSpecification(
        override val singletonType: SingletonType,
        override val bufferSize: Int,
        override val idleTimeout: Duration,
        override val fromBeginning: Boolean
    ) : AbstractStepSpecification<Int, Int, TestSingletonSpecification>(),
        SingletonStepSpecification<Int, Int, TestSingletonSpecification>


    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = SingletonStepSpecificationConverter()

        // when+then
        Assertions.assertTrue(converter.support(relaxedMockk<SingletonProxyStepSpecification<*>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = SingletonStepSpecificationConverter()

        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec without name`() {
        // given
        val receiveChannel: ReceiveChannel<String> = relaxedMockk()
        val singletonOutputStep: SingletonOutputDecorator<Int, String> = relaxedMockk {
            every { subscribe() } returns receiveChannel
        }
        val nextStep: StepSpecification<String, *, *> = relaxedMockk()
        val spec = SingletonProxyStepSpecification(
            nextStep,
            singletonOutputStep,
            SingletonType.BROADCAST,
            123,
            Duration.ofMillis(456),
            true
        )
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = SingletonStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<SingletonProxyStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(SingletonProxyStep::class)
                prop("subscriptionChannel").isSameAs(receiveChannel)
                typedProp<Step<*, *>, Topic>("topic").all {
                    transform { topic -> topic::class.simpleName }.isEqualTo("BroadcastFromBeginningTopic")
                    typedProp<Topic, Collection<*>>("buffer").prop("maxSize").isEqualTo(123)
                    prop("idleTimeout").isEqualTo(Duration.ofMillis(456))
                }
            }
        }
    }

}