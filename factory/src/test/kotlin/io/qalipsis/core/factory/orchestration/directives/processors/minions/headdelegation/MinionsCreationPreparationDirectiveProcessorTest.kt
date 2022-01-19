package io.qalipsis.core.factory.orchestration.directives.processors.minions.headdelegation

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.core.directives.MinionsCreationDirective
import io.qalipsis.core.directives.MinionsCreationPreparationDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.ScenariosRegistry
import io.qalipsis.core.factory.testDag
import io.qalipsis.core.factory.testScenario
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
@WithMockk
internal class MinionsCreationPreparationDirectiveProcessorTest {

    @RelaxedMockK
    lateinit var scenariosRegistry: ScenariosRegistry

    @RelaxedMockK
    lateinit var directiveRegistry: DirectiveRegistry

    @RelaxedMockK
    lateinit var directiveProducer: DirectiveProducer

    @RelaxedMockK
    lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    lateinit var factoryConfiguration: FactoryConfiguration

    @SpyK
    var idGenerator = TestIdGenerator

    lateinit var processor: MinionsCreationPreparationDirectiveProcessor

    @BeforeEach
    fun setup(){
        processor = MinionsCreationPreparationDirectiveProcessor(scenariosRegistry, directiveRegistry, directiveProducer, feedbackFactoryChannel, idGenerator, factoryConfiguration)
    }

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsCreationPreparationDirective() {
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", channel = "broadcast")
        every { scenariosRegistry.contains("my-scenario") } returns true

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
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", channel = "broadcast")
        every { scenariosRegistry.contains("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotProcessWhenScenarioNotFound() = runBlockingTest {
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", channel = "broadcast")
        every { scenariosRegistry.get("my-scenario") } returns null

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            scenariosRegistry.get("my-scenario")
        }

        confirmVerified(directiveRegistry, scenariosRegistry, feedbackFactoryChannel, directiveProducer)
    }

    @Test
    @Timeout(5)
    internal fun shouldCreateDirectivesForEachDag() = runBlockingTest {
        // given
        val directive = MinionsCreationPreparationDirectiveReference("my-directive", "my-campaign", "my-scenario", channel = "broadcast")
        coEvery { directiveRegistry.read(refEq(directive)) } returns 123
        val scenario = testScenario("my-scenario", minionsCount = 2) {
            this.createIfAbsent("my-dag-1") { testDag("my-dag-1", this, isUnderLoad = true) }
            this.createIfAbsent("my-dag-2") {
                testDag("my-dag-2", this, isUnderLoad = true, isSingleton = true)
            }
            this.createIfAbsent("my-dag-3") {
                testDag("my-dag-3", this, isUnderLoad = false, isSingleton = false)
            }
        }
        every { scenariosRegistry.get("my-scenario") } returns scenario
        val createdDirectives = mutableListOf<MinionsCreationDirective>()
        val feedbacks = mutableListOf<DirectiveFeedback>()
        coEvery { feedbackFactoryChannel.publish(capture(feedbacks)) } returns Unit
        coEvery { directiveProducer.publish(capture(createdDirectives)) } returns Unit

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            scenariosRegistry.get("my-scenario")
            directiveRegistry.read(refEq(directive))
            feedbackFactoryChannel.publish(any())
            directiveProducer.publish(any())
            directiveProducer.publish(any())
            directiveProducer.publish(any())
            feedbackFactoryChannel.publish(any())
        }
        assertThat(feedbacks).all {
            each {
                it.isInstanceOf(DirectiveFeedback::class)
                it.prop(DirectiveFeedback::directiveKey).isEqualTo(directive.key)
            }
            index(0).prop(DirectiveFeedback::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
            index(1).prop(DirectiveFeedback::status).isEqualTo(FeedbackStatus.COMPLETED)
        }

        assertThat(createdDirectives.sortedBy(MinionsCreationDirective::dagId)).all {
            each {
                it.all {
                    prop(MinionsCreationDirective::scenarioId).isEqualTo("my-scenario")
                    prop(MinionsCreationDirective::campaignId).isEqualTo("my-campaign")
                }
            }
            index(0).all {
                prop(MinionsCreationDirective::dagId).isEqualTo("my-dag-1")
                prop(MinionsCreationDirective::queue).hasSize(123)
            }
            index(1).all {
                prop(MinionsCreationDirective::dagId).isEqualTo("my-dag-2")
                prop(MinionsCreationDirective::queue).hasSize(1)
            }
            index(2).all {
                prop(MinionsCreationDirective::dagId).isEqualTo("my-dag-3")
                prop(MinionsCreationDirective::queue).hasSize(1)
            }
        }

        confirmVerified(directiveRegistry, scenariosRegistry, feedbackFactoryChannel, directiveProducer)
    }
}