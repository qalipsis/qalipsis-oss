package io.evolue.core.factory.steps.correlation

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.orchestration.Scenario
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.steps.CorrelationStepSpecification
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.test.assertk.prop
import io.evolue.test.assertk.typedProp
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import io.mockk.coEvery
import io.mockk.every
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class CorrelationStepSpecificationConverterTest {

    @Test
    internal fun `should support expected spec`() {
        // given
        val converter = CorrelationStepSpecificationConverter()

        // when+then
        assertTrue(converter.support(relaxedMockk<CorrelationStepSpecification<*, *>>()))
    }

    @Test
    internal fun `should not support unexpected spec`() {
        // given
        val converter = CorrelationStepSpecificationConverter()

        // when+then
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name to step`() {
        // given
        val primaryKeyExtractor: CorrelationRecord<Int>.() -> Any? = { }
        val secondaryKeyExtractor: CorrelationRecord<out Any?>.() -> Any? = { }
        val spec = CorrelationStepSpecification<Int, String>(primaryKeyExtractor, secondaryKeyExtractor, "other-step")
        spec.name = "my-step"
        spec.cacheTimeout = Duration.ofMillis(123)

        val otherStep: Step<*, *> = relaxedMockk {
            every { id } returns "the-other-step"
        }
        val scenarioSpec: MutableScenarioSpecification = relaxedMockk {
            every { exists("other-step") } returns true
        }
        val scen: Scenario = relaxedMockk {
            coEvery { findStep("other-step") } returns otherStep
        }
        val dag: DirectedAcyclicGraph = relaxedMockk {
            every { scenario } returns scen
        }
        val creationContext = StepCreationContextImpl(scenarioSpec, dag, spec)

        val converter = CorrelationStepSpecificationConverter()

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<CorrelationStepSpecification<*, *>>)
        }

        // then
        verifyOnce { scenarioSpec.exists("other-step") }

        // Verify that the secondary step is replaced in the scenario by its decorator.
        val replacedStepSlot = slot<CorrelationOutputDecorator<*, *>>()
        coVerifyOnce {
            scen.findStep("other-step")
            scen.addStep(capture(replacedStepSlot))
        }
        assertThat(replacedStepSlot.captured).all {
            isInstanceOf(CorrelationOutputDecorator::class)
            prop("decorated").isSameAs(otherStep)
        }

        creationContext.createdStep!!.let {
            assertEquals("my-step", it.id)
            assertThat(it).all {
                isInstanceOf(CorrelationStep::class)
                prop("correlationKeyExtractor").isSameAs(primaryKeyExtractor)
                typedProp<Step<*, *>, Collection<SecondaryCorrelation>>("secondaryCorrelations").all {
                    hasSize(1)
                    transform { correlations -> correlations.first() }.all {
                        prop("sourceStepId").isEqualTo("the-other-step")
                        prop("subscriptionChannel").isNotNull()
                        prop("keyExtractor").isSameAs(secondaryKeyExtractor)
                    }
                }
            }
        }
    }


}
