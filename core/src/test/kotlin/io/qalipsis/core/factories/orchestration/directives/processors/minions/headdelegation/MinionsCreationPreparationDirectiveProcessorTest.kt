package io.qalipsis.core.factories.orchestration.directives.processors.minions.headdelegation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.core.cross.directives.MinionsCreationDirective
import io.qalipsis.core.cross.directives.MinionsCreationPreparationDirectiveReference
import io.qalipsis.core.cross.directives.TestDescriptiveDirective
import io.qalipsis.core.factories.orchestration.ScenariosKeeper
import io.qalipsis.test.coroutines.CleanCoroutines
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
@WithMockk
@CleanCoroutines
internal class MinionsCreationPreparationDirectiveProcessorTest {

    @RelaxedMockK
    lateinit var scenariosKeeper: ScenariosKeeper

    @RelaxedMockK
    lateinit var directiveRegistry: DirectiveRegistry

    @RelaxedMockK
    lateinit var directiveProducer: DirectiveProducer

    @RelaxedMockK
    lateinit var feedbackProducer: FeedbackProducer

    @InjectMockKs
    lateinit var processor: MinionsCreationPreparationDirectiveProcessor

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsCreationPreparationDirective() {
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptNotMinionsCreationPreparationDirective() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptMinionsCreationPreparationDirectiveForUnknownScenario() {
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotProcessWhenScenarioNotFound() {
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario")
        every { scenariosKeeper.getScenario("my-scenario") } returns null

        // when
        runBlocking {
            processor.process(directive)
        }

        // then
        coVerifyOrder {
            scenariosKeeper.getScenario("my-scenario")
        }

        confirmVerified(directiveRegistry, scenariosKeeper, feedbackProducer, directiveProducer)
    }

    @Test
    @Timeout(1)
    internal fun shouldCreateDirectivesForEachDag() {
        // given
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario")
        coEvery { directiveRegistry.read(refEq(directive)) } returns 123
        val createdDirectives = mutableListOf<MinionsCreationDirective>()
        val feedbacks = mutableListOf<DirectiveFeedback>()
        every { scenariosKeeper.getScenario("my-scenario") } returns
                Scenario(
                        "my-scenario",
                        mutableListOf(
                                DirectedAcyclicGraph("my-dag-1",
                                        Scenario("my-scenario", rampUpStrategy = relaxedMockk()), false, false),
                                DirectedAcyclicGraph("my-dag-2",
                                        Scenario("my-scenario", rampUpStrategy = relaxedMockk()), false, false)
                        ),
                        relaxedMockk()
                )
        coEvery { feedbackProducer.publish(capture(feedbacks)) } answers {}
        coEvery { directiveProducer.publish(capture(createdDirectives)) } answers {}

        // when
        runBlocking {
            processor.process(directive)
        }

        // then
        coVerifyOrder {
            scenariosKeeper.getScenario("my-scenario")
            directiveRegistry.read(refEq(directive))
            feedbackProducer.publish(any())
            directiveProducer.publish(any())
            directiveProducer.publish(any())
            feedbackProducer.publish(any())
        }
        feedbacks.forEach {
            assertThat(it).isInstanceOf(DirectiveFeedback::class)
            assertThat(it.directiveKey).isEqualTo(directive.key)
        }
        assertThat(feedbacks[0]::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedbacks[1]::status).isEqualTo(FeedbackStatus.COMPLETED)

        createdDirectives.forEach {
            assertThat(it).isInstanceOf(MinionsCreationDirective::class)
            assertThat(it.scenarioId).isEqualTo("my-scenario")
            assertThat(it.queue.toSet().size).isEqualTo(123)
        }
        assertThat(createdDirectives[0]::dagId).isEqualTo("my-dag-1")
        assertThat(createdDirectives[1]::dagId).isEqualTo("my-dag-2")
        confirmVerified(directiveRegistry, scenariosKeeper, feedbackProducer, directiveProducer)
    }
}
