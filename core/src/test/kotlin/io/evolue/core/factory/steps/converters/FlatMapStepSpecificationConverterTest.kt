package io.evolue.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.FlatMapStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.core.factory.steps.FlatMapStep
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.mockk.every
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class FlatMapStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = FlatMapStepSpecificationConverter()

        // when+then
        assertTrue(converter.support(relaxedMockk<FlatMapStepSpecification<*, *>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = FlatMapStepSpecificationConverter()

        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> Flow<String> = { _ -> emptyFlow() }
        val spec = FlatMapStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.retryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val converter = FlatMapStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FlatMapStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(FlatMapStep::class)
                prop("retryPolicy").isSameAs(spec.retryPolicy)
                prop("block").isSameAs(blockSpecification)
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() {
        // given
        val blockSpecification: (input: Int) -> Flow<String> = { _ -> emptyFlow() }
        val spec = FlatMapStepSpecification(blockSpecification)

        val mockedRetryPolicy: RetryPolicy = relaxedMockk()
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk {
            every { scenario.defaultRetryPolicy } returns mockedRetryPolicy
        }, spec)

        val converter = FlatMapStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<FlatMapStepSpecification<*, *>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertNotNull(it.id)
            assertThat(it).all {
                isInstanceOf(FlatMapStep::class)
                prop("retryPolicy").isSameAs(mockedRetryPolicy)
                prop("block").isSameAs(blockSpecification)
            }
        }
    }

}