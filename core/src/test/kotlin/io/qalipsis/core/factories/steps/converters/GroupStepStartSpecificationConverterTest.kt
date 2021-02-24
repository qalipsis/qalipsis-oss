package io.qalipsis.core.factories.steps.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.key
import io.mockk.every
import io.qalipsis.api.steps.GroupStepEndSpecification
import io.qalipsis.api.steps.GroupStepSpecification
import io.qalipsis.api.steps.GroupStepStartSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.core.factories.steps.GroupStep
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import io.qalipsis.test.utils.getProperty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@ExperimentalCoroutinesApi
internal class GroupStepStartSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<GroupStepSpecificationConverter>() {

    @AfterEach
    internal fun tearDown() {
        converter.getProperty<MutableMap<String, GroupStep<*, *>>>("startStepsById").clear()
    }

    @Test
    override fun `should support expected spec`() {
        // when+then
        Assertions.assertTrue(converter.support(relaxedMockk<GroupStepSpecification<*, *>>()))
        Assertions.assertTrue(converter.support(relaxedMockk<GroupStepStartSpecification<*>>()))
        Assertions.assertTrue(converter.support(relaxedMockk<GroupStepEndSpecification<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert group spec without name nor retry policy to step`() = runBlockingTest {
        // given
        val spec = GroupStepStartSpecification<Int>()
        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<GroupStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(GroupStep::class).all {
            prop("id").isNotNull()
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
        }
        // The name is set in the spec.
        assertThat(spec.name).isEqualTo(creationContext.createdStep!!.id)
        assertThat(converter).typedProp<Map<String, GroupStep<*, *>>>("startStepsById").all {
            hasSize(1)
            key(creationContext.createdStep!!.id).isSameAs(creationContext.createdStep)
        }
    }

    @Test
    internal fun `should convert group spec with name and retry policy to step`() = runBlockingTest {
        // given
        val spec = GroupStepStartSpecification<Int>()
        spec.name = "my-step"
        spec.retryPolicy = mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<GroupStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(GroupStep::class).all {
            prop("id").isEqualTo("my-step")
            prop("retryPolicy").isSameAs(mockedRetryPolicy)
        }
        assertThat(spec.name).isEqualTo("my-step")
        assertThat(converter).typedProp<Map<String, GroupStep<*, *>>>("startStepsById").all {
            hasSize(1)
            key("my-step").isSameAs(creationContext.createdStep)
        }
    }

    @Test
    internal fun `should convert end group spec without name to step`() = runBlockingTest {
        // given
        val startSpec = GroupStepStartSpecification<Int>()
        startSpec.name = "the-group-start-spec"
        val groupStep: GroupStep<*, *> = relaxedMockk()
        converter.getProperty<MutableMap<String, GroupStep<*, *>>>("startStepsById")["the-group-start-spec"] = groupStep

        val spec = GroupStepEndSpecification<Int, String>(startSpec)
        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<GroupStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(GroupStepSpecificationConverter.GroupEndProxy::class)
            .all {
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("start").isSameAs(groupStep)
            }
        assertThat(converter).typedProp<Map<String, GroupStep<*, *>>>("startStepsById").isEmpty()
    }

    @Test
    internal fun `should convert end group spec with name to step`() = runBlockingTest {
        // given
        val startSpec = GroupStepStartSpecification<Int>()
        startSpec.name = "the-group-start-spec"
        val groupStep: GroupStep<*, *> = relaxedMockk()
        converter.getProperty<MutableMap<String, GroupStep<*, *>>>("startStepsById")["the-group-start-spec"] = groupStep

        val spec = GroupStepEndSpecification<Int, String>(startSpec)
        spec.name = "my-step"

        every { directedAcyclicGraph.scenario.defaultRetryPolicy } returns mockedRetryPolicy
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        @Suppress("UNCHECKED_CAST")
        converter.convert<Int, String>(creationContext as StepCreationContext<GroupStepSpecification<*, *>>)

        // then
        assertThat(creationContext.createdStep!!).isInstanceOf(GroupStepSpecificationConverter.GroupEndProxy::class)
            .all {
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("start").isSameAs(groupStep)
            }
        assertThat(converter).typedProp<Map<String, GroupStep<*, *>>>("startStepsById").isEmpty()
    }
}
