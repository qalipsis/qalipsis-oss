package io.qalipsis.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.mockk.every
import io.qalipsis.api.steps.CollectionStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factory.steps.CollectionStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

@Suppress("UNCHECKED_CAST")
internal class CollectionStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<CollectionStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<CollectionStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert minimal spec with name and retry policy to step`() = testCoroutineDispatcher.runTest {
        // given
        val spec = CollectionStepSpecification<Int>(null, 0)
        spec.name = "my-step"
        spec.retryPolicy = mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<Int, List<Int>>(creationContext as StepCreationContext<CollectionStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(CollectionStep::class).all {
            prop(CollectionStep<*>::name).isEqualTo("my-step")
            prop("retryPolicy").isNull()
            prop("timeout").isNull()
            prop("batchSize").isEqualTo(Int.MAX_VALUE)
            prop("coroutineScope").isSameAs(campaignCoroutineScope)
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() = testCoroutineDispatcher.runTest {
        // given
        val spec = CollectionStepSpecification<Int>(Duration.ofSeconds(123), 7127654)
        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(relaxedMockk(), directedAcyclicGraph, spec)

        // when
        converter.convert<Int, List<Int>>(creationContext as StepCreationContext<CollectionStepSpecification<*>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(CollectionStep::class).all {
            prop(CollectionStep<*>::name).isEmpty()
            prop("retryPolicy").isNull()
            prop("timeout").isEqualTo(Duration.ofSeconds(123))
            prop("batchSize").isEqualTo(7127654)
            prop("coroutineScope").isSameAs(campaignCoroutineScope)
        }
    }

}
