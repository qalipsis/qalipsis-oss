package io.qalipsis.core.factory.orchestration.directives.processors.minions.headdelegation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.slot
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.orchestration.directives.DirectiveProducer
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.rampup.MinionsStartingLine
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.ScenariosRegistry
import io.qalipsis.core.feedbacks.FeedbackFactoryChannel
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.QalipsisTimeAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class MinionsRampUpPreparationDirectiveProcessorTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var scenariosRegistry: ScenariosRegistry

    @RelaxedMockK
    lateinit var directiveProducer: DirectiveProducer

    @RelaxedMockK
    lateinit var feedbackFactoryChannel: FeedbackFactoryChannel

    @RelaxedMockK
    lateinit var minionsCreationPreparationDirectiveProcessor: MinionsCreationPreparationDirectiveProcessor

    @RelaxedMockK
    lateinit var factoryConfiguration: FactoryConfiguration

    @SpyK
    var idGenerator = TestIdGenerator

    @InjectMockKs
    lateinit var processor: MinionsRampUpPreparationDirectiveProcessor

    @Test
    @Timeout(2)
    internal fun shouldAcceptMinionsRampUpPreparationDirective() {
        val directive = MinionsRampUpPreparationDirective("my-campaign", "my-scenario", channel = "broadcast", key = idGenerator.short())
        every { scenariosRegistry.contains("my-scenario") } returns true
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf<ScenarioId, List<MinionId>>(
            directive.scenarioId to emptyList()
        )

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(2)
    internal fun shouldNotAcceptNotMinionsRampUpPreparationDirective() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(2)
    internal fun shouldNotAcceptMinionsRampUpPreparationDirectiveForUnknownScenario() {
        val directive = MinionsRampUpPreparationDirective("my-campaign", "my-scenario", channel = "broadcast", key = idGenerator.short())
        every { scenariosRegistry.contains("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(2)
    internal fun shouldNotAcceptMinionsRampUpPreparationDirectiveWhenMinionsWereNotCreatedLocally() {
        val directive = MinionsRampUpPreparationDirective("my-campaign", "my-scenario", channel = "broadcast", key = idGenerator.short())
        every { scenariosRegistry.contains("my-scenario") } returns true
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf<ScenarioId, List<MinionId>>()

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(2)
    internal fun shouldNotProcessWhenScenarioNotFound() = testCoroutineDispatcher.run {
        val directive = MinionsRampUpPreparationDirective("my-campaign", "my-scenario", channel = "broadcast", key = idGenerator.short())
        every { scenariosRegistry.get("my-scenario") } returns null

        // when
        processor.process(directive)

        // then
        coVerifyOrder {
            scenariosRegistry.get("my-scenario")
        }

        confirmVerified(
            scenariosRegistry,
            feedbackFactoryChannel,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }

    @Test
    @Timeout(2)
    internal fun shouldThrowExceptionWhenMinionsToStartIsZero() = testCoroutineDispatcher.runTest {
        // given
        val directive = MinionsRampUpPreparationDirective("my-campaign", "my-scenario", 2000, 3.0, channel = "broadcast", key = idGenerator.short())
        val createdDirective = slot<MinionsStartDirective>()
        val feedbacks = mutableListOf<DirectiveFeedback>()
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf(
            directive.scenarioId to (0..27).map { "minion-$it" }.toList()
        )

        every { scenariosRegistry.get("my-scenario") } returns relaxedMockk { }

        coEvery { feedbackFactoryChannel.publish(capture(feedbacks)) } answers {}
        coEvery { directiveProducer.publish(capture(createdDirective)) } answers {}

        // when
        assertThrows<IllegalArgumentException> {
            processor.process(directive)
        }

        // then
        coVerifyOrder {
            scenariosRegistry.get("my-scenario")
            feedbackFactoryChannel.publish(any())
            minionsCreationPreparationDirectiveProcessor.minions
            feedbackFactoryChannel.publish(any())
        }
        feedbacks.forEach {
            assertThat(it).isInstanceOf(DirectiveFeedback::class)
            assertThat(it.directiveKey).isEqualTo(directive.key)
        }
        assertThat(feedbacks[0]::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedbacks[1]::status).isEqualTo(FeedbackStatus.FAILED)

        confirmVerified(
            scenariosRegistry,
            feedbackFactoryChannel,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }

    @Test
    @Timeout(2)
    internal fun shouldThrowExceptionWhenStartOffsetIsZero() = testCoroutineDispatcher.runTest {
        // given
        val directive = MinionsRampUpPreparationDirective(
            "my-campaign", "my-scenario", 2000, 2.0,
            channel = "broadcast", key = idGenerator.short()
        )
        val createdDirective = slot<MinionsStartDirective>()
        val feedbacks = mutableListOf<DirectiveFeedback>()
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf(
            directive.scenarioId to (0..27).map { "minion-$it" }.toList()
        )

        val mockedRampupStrategy: RampUpStrategy = relaxedMockk {
            every { iterator(any(), any()) } returns relaxedMockk {
                every { next() } returns MinionsStartingLine(3, 0)
            }
        }
        every { scenariosRegistry.get("my-scenario") } returns relaxedMockk {
            every { rampUpStrategy } returns mockedRampupStrategy
        }

        coEvery { feedbackFactoryChannel.publish(capture(feedbacks)) } answers {}
        coEvery { directiveProducer.publish(capture(createdDirective)) } answers {}

        // when
        assertThrows<IllegalArgumentException> {
            processor.process(directive)
        }

        // then
        coVerifyOrder {
            scenariosRegistry.get("my-scenario")
            feedbackFactoryChannel.publish(any())
            minionsCreationPreparationDirectiveProcessor.minions
            mockedRampupStrategy.iterator(28, 2.0)
            feedbackFactoryChannel.publish(any())
        }
        feedbacks.forEach {
            assertThat(it).isInstanceOf(DirectiveFeedback::class)
            assertThat(it.directiveKey).isEqualTo(directive.key)
        }
        assertThat(feedbacks[0]::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedbacks[1]::status).isEqualTo(FeedbackStatus.FAILED)

        confirmVerified(
            scenariosRegistry,
            feedbackFactoryChannel,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }

    @Test
    @Timeout(2)
    internal fun shouldCreateOneDirectiveWithAllTheStarts() = testCoroutineDispatcher.runTest {
        // given
        val directive = MinionsRampUpPreparationDirective("my-campaign", "my-scenario", 2000, 2.0, channel = "broadcast", key = idGenerator.short())
        val createdDirective = slot<MinionsStartDirective>()
        val feedbacks = mutableListOf<DirectiveFeedback>()
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf(
            directive.scenarioId to (0..27).toList().map { "minion-$it" }
        )

        // Scenario with a ramp-up to start 3 minions every 500 ms.
        val mockedRampupStrategy: RampUpStrategy = relaxedMockk {
            every { iterator(any(), any()) } returns relaxedMockk {
                every { next() } returns MinionsStartingLine(3, 500)
            }
        }
        every { scenariosRegistry.get("my-scenario") } returns relaxedMockk {
            every { id } returns "my-scenario"
            every { rampUpStrategy } returns mockedRampupStrategy
        }

        coEvery { feedbackFactoryChannel.publish(capture(feedbacks)) } answers {}
        coEvery { directiveProducer.publish(capture(createdDirective)) } answers {}

        // when
        val start = System.currentTimeMillis() + 2000
        processor.process(directive)

        // then
        coVerifyOrder {
            scenariosRegistry.get("my-scenario")
            feedbackFactoryChannel.publish(any())
            minionsCreationPreparationDirectiveProcessor.minions
            mockedRampupStrategy.iterator(28, 2.0)
            directiveProducer.publish(any())
            feedbackFactoryChannel.publish(any())
        }
        feedbacks.forEach {
            assertThat(it).isInstanceOf(DirectiveFeedback::class)
            assertThat(it.directiveKey).isEqualTo(directive.key)
        }
        assertThat(feedbacks[0]::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedbacks[1]::status).isEqualTo(FeedbackStatus.COMPLETED)

        createdDirective.captured.apply {
            assertThat(this).isInstanceOf(MinionsStartDirective::class)
            assertThat(this.scenarioId).isEqualTo("my-scenario")
            repeat(9) { startIndex ->
                val expectedStart = start + (startIndex + 1) * 500
                repeat(3) { i ->
                    val itemIndex = startIndex * 3 + i
                    Assertions.assertEquals("minion-$itemIndex", this.set[itemIndex].minionId)
                    QalipsisTimeAssertions.assertInstantAfterOrEqual(
                        expectedStart, this.set[itemIndex].timestamp,
                        "Start line for item $itemIndex"
                    )
                }
            }
            QalipsisTimeAssertions.assertInstantAfterOrEqual(start + 5000, this.set[27].timestamp)
            Assertions.assertEquals("minion-27", this.set[27].minionId)
        }

        confirmVerified(
            scenariosRegistry,
            feedbackFactoryChannel,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }
}
