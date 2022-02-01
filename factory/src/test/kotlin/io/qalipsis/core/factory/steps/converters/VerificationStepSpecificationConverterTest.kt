package io.qalipsis.core.factory.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.VerificationStepSpecification
import io.qalipsis.core.factory.steps.VerificationStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class VerificationStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<VerificationStepSpecificationConverter>() {

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<VerificationStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step and ignore the error reporting`() = runBlockingTest {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = VerificationStepSpecification(blockSpecification)
        spec.name = "my-step"
        spec.reporting.reportErrors = true
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<VerificationStepSpecification<*, *>>)

        // then
        assertThat(spec.reporting.reportErrors).isFalse()
        assertThat(creationContext.createdStep!!).isInstanceOf(VerificationStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("reportLiveStateRegistry").isSameAs(campaignReportLiveStateRegistry)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("assertionBlock").isSameAs(blockSpecification)
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlockingTest {
        // given
        val blockSpecification: suspend (input: String) -> Int = { value -> value.toInt() }
        val spec = VerificationStepSpecification(blockSpecification)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<VerificationStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(VerificationStep::class).all {
            prop(VerificationStep<*,*>::id).isEmpty()
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("assertionBlock").isSameAs(blockSpecification)
        }
    }
}
