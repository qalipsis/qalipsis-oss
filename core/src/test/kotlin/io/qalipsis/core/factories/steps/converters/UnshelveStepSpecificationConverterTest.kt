package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.states.SharedStateRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.UnshelveStepSpecification
import io.qalipsis.core.factories.steps.SingularUnshelveStep
import io.qalipsis.core.factories.steps.UnshelveStep
import io.qalipsis.core.factories.steps.VerificationStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class UnUnshelveStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<UnshelveStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var sharedStateRegistry: SharedStateRegistry

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<UnshelveStepSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() = runBlockingTest {
        // given
        val keys = listOf("value-1", "value-2")
        val spec = UnshelveStepSpecification<String, Map<String, Any?>>(keys, true, false)
        spec.name = "my-step"
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        // when
        converter.convert<String, String>(creationContext as StepCreationContext<UnshelveStepSpecification<*, *>>)

        // then
        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).isInstanceOf(UnshelveStep::class).all {
                prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
                prop("names").isSameAs(keys)
                prop("delete").isEqualTo(true)
            }
        }
    }

    @Test
    internal fun `should convert spec without name to step`() = runBlockingTest {
        // given
        val keys = listOf("value-1", "value-2")
        val spec = UnshelveStepSpecification<String, Map<String, Any?>>(keys, false, false)
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        // when
        converter.convert<String, String>(creationContext as StepCreationContext<UnshelveStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(UnshelveStep::class).all {
            prop(UnshelveStep<*>::id).isEmpty()
            prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
            prop("names").isSameAs(keys)
            prop("delete").isEqualTo(false)
        }
    }

    @Test
    internal fun `should convert singular spec without name to step`() = runBlockingTest {
        // given
        val keys = listOf("value-1")
        val spec = UnshelveStepSpecification<String, Map<String, Any?>>(keys, true, true)
        val creationContext = StepCreationContextImpl(relaxedMockk(), relaxedMockk(), spec)

        val sharedStateRegistry: SharedStateRegistry = relaxedMockk()
        val converter = UnshelveStepSpecificationConverter(sharedStateRegistry)

        // when
        converter.convert<String, String>(creationContext as StepCreationContext<UnshelveStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(SingularUnshelveStep::class).all {
            prop(SingularUnshelveStep<*,*>::id).isEmpty()
            prop("sharedStateRegistry").isSameAs(sharedStateRegistry)
            prop("name").isEqualTo("value-1")
            prop("delete").isEqualTo(true)
        }
    }
}
