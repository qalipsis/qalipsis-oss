package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.mockk.every
import io.qalipsis.api.steps.FlatMapStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factories.steps.FlatMapStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class FlatMapStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<FlatMapStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<FlatMapStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> Flow<String> = { _ -> emptyFlow() }
        val spec = FlatMapStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.retryPolicy = mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FlatMapStepSpecification<*, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(FlatMapStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
            prop("block").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> Flow<String> = { _ -> emptyFlow() }
        val spec = FlatMapStepSpecification(blockSpecification)

        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FlatMapStepSpecification<*, *>>)
        }

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(FlatMapStep::class).all {
            prop("id").isNotNull()
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
            prop("block").isSameAs(blockSpecification)
        }
    }

}
