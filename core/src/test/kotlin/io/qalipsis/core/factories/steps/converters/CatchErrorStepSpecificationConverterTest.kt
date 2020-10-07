package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.qalipsis.api.context.StepError
import io.qalipsis.api.steps.CatchErrorStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factories.steps.CatchErrorStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class CatchErrorStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<CatchErrorStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<CatchErrorStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val blockSpecification: (error: Collection<StepError>) -> Unit = {}
        val spec = CatchErrorStepSpecification<Int>(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<CatchErrorStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(CatchErrorStep::class)
            prop("id").isEqualTo("my-step")
            prop("block").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name to step`() {
        // given
        val blockSpecification: (error: Collection<StepError>) -> Unit = {}
        val spec = CatchErrorStepSpecification<Int>(blockSpecification)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<CatchErrorStepSpecification<*>>)
        }

        // then
        assertThat(creationContext.createdStep!!).all {
            isInstanceOf(CatchErrorStep::class)
            prop("id").isNotNull()
            prop("block").isSameAs(blockSpecification)
        }
    }
}
