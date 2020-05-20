package io.evolue.core.factory.orchestration.directives.processors.minions.headdelegation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.evolue.api.context.MinionId
import io.evolue.api.context.ScenarioId
import io.evolue.api.rampup.MinionsStartingLine
import io.evolue.core.cross.driving.TestDescriptiveDirective
import io.evolue.core.cross.driving.directives.DirectiveProducer
import io.evolue.core.cross.driving.directives.MinionsRampUpPreparationDirective
import io.evolue.core.cross.driving.directives.MinionsStartDirective
import io.evolue.core.cross.driving.feedback.DirectiveFeedback
import io.evolue.core.cross.driving.feedback.FeedbackProducer
import io.evolue.core.cross.driving.feedback.FeedbackStatus
import io.evolue.core.factory.orchestration.ScenariosKeeper
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.time.EvolueTimeAssertions
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class MinionsRampUpPreparationDirectiveProcessorTest {

    @RelaxedMockK
    lateinit var scenariosKeeper: ScenariosKeeper

    @RelaxedMockK
    lateinit var directiveProducer: DirectiveProducer

    @RelaxedMockK
    lateinit var feedbackProducer: FeedbackProducer

    @RelaxedMockK
    lateinit var minionsCreationPreparationDirectiveProcessor: MinionsCreationPreparationDirectiveProcessor

    @InjectMockKs
    lateinit var processor: MinionsRampUpPreparationDirectiveProcessor

    @Test
    @Timeout(1)
    internal fun shouldAcceptMinionsRampUpPreparationDirective() {
        val directive = MinionsRampUpPreparationDirective("my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns true
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf<ScenarioId, List<MinionId>>(
            directive.scenarioId to emptyList()
        )

        Assertions.assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptNotMinionsRampUpPreparationDirective() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptMinionsRampUpPreparationDirectiveForUnknownScenario() {
        val directive = MinionsRampUpPreparationDirective("my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotAcceptMinionsRampUpPreparationDirectiveWhenMinionsWereNotCreatedLocally() {
        val directive = MinionsRampUpPreparationDirective("my-scenario")
        every { scenariosKeeper.hasScenario("my-scenario") } returns true
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf<ScenarioId, List<MinionId>>()

        Assertions.assertFalse(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    internal fun shouldNotProcessWhenScenarioNotFound() {
        val directive = MinionsRampUpPreparationDirective("my-scenario")
        every { scenariosKeeper.getScenario("my-scenario") } returns null

        // when
        runBlocking {
            processor.process(directive)
        }

        // then
        coVerifyOrder {
            scenariosKeeper.getScenario("my-scenario")
        }

        confirmVerified(
            scenariosKeeper,
            feedbackProducer,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }

    @Test
    @Timeout(1)
    internal fun shouldThrowExceptionWhenMinionsToStartIsZero() {
        // given
        val directive = MinionsRampUpPreparationDirective("my-scenario", 2000, 3.0)
        val createdDirective = slot<MinionsStartDirective>()
        val feedbacks = mutableListOf<DirectiveFeedback>()
        every { minionsCreationPreparationDirectiveProcessor getProperty "minions" } returns mutableMapOf(
            directive.scenarioId to (0..27).map { "minion-$it" }.toList()
        )

        every { scenariosKeeper.getScenario("my-scenario") } returns relaxedMockk { }

        coEvery { feedbackProducer.publish(capture(feedbacks)) } answers {}
        coEvery { directiveProducer.publish(capture(createdDirective)) } answers {}

        // when
        assertThrows<IllegalArgumentException> {
            runBlocking {
                processor.process(directive)
            }
        }

        // then
        coVerifyOrder {
            scenariosKeeper.getScenario("my-scenario")
            feedbackProducer.publish(any())
            minionsCreationPreparationDirectiveProcessor.minions
            feedbackProducer.publish(any())
        }
        feedbacks.forEach {
            assertThat(it).isInstanceOf(DirectiveFeedback::class)
            assertThat(it.directiveKey).isEqualTo(directive.key)
        }
        assertThat(feedbacks[0]::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedbacks[1]::status).isEqualTo(FeedbackStatus.FAILED)

        confirmVerified(
            scenariosKeeper,
            feedbackProducer,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }

    @Test
    @Timeout(1)
    internal fun shouldThrowExceptionWhenStartOffsetIsZero() {
        // given
        val directive = MinionsRampUpPreparationDirective("my-scenario", 2000, 2.0)
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
        every { scenariosKeeper.getScenario("my-scenario") } returns relaxedMockk {
            every { rampUpStrategy } returns mockedRampupStrategy
        }

        coEvery { feedbackProducer.publish(capture(feedbacks)) } answers {}
        coEvery { directiveProducer.publish(capture(createdDirective)) } answers {}

        // when
        assertThrows<IllegalArgumentException> {
            runBlocking {
                processor.process(directive)
            }
        }

        // then
        coVerifyOrder {
            scenariosKeeper.getScenario("my-scenario")
            feedbackProducer.publish(any())
            minionsCreationPreparationDirectiveProcessor.minions
            mockedRampupStrategy.iterator(28, 2.0)
            feedbackProducer.publish(any())
        }
        feedbacks.forEach {
            assertThat(it).isInstanceOf(DirectiveFeedback::class)
            assertThat(it.directiveKey).isEqualTo(directive.key)
        }
        assertThat(feedbacks[0]::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
        assertThat(feedbacks[1]::status).isEqualTo(FeedbackStatus.FAILED)

        confirmVerified(
            scenariosKeeper,
            feedbackProducer,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }

    @Test
    @Timeout(1)
    internal fun shouldCreateOneDirectiveWithAllTheStarts() {
        // given
        val directive = MinionsRampUpPreparationDirective("my-scenario", 2000, 2.0)
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
        every { scenariosKeeper.getScenario("my-scenario") } returns relaxedMockk {
            every { id } returns "my-scenario"
            every { rampUpStrategy } returns mockedRampupStrategy
        }

        coEvery { feedbackProducer.publish(capture(feedbacks)) } answers {}
        coEvery { directiveProducer.publish(capture(createdDirective)) } answers {}

        // when
        val start = System.currentTimeMillis() + 2000
        runBlocking {
            processor.process(directive)
        }

        // then
        coVerifyOrder {
            scenariosKeeper.getScenario("my-scenario")
            feedbackProducer.publish(any())
            minionsCreationPreparationDirectiveProcessor.minions
            mockedRampupStrategy.iterator(28, 2.0)
            directiveProducer.publish(any())
            feedbackProducer.publish(any())
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
                    EvolueTimeAssertions.assertInstantAfter(expectedStart, this.set[itemIndex].timestamp,
                        "Start line for item $itemIndex")
                }
            }
            EvolueTimeAssertions.assertInstantAfter(start + 5000, this.set[27].timestamp)
            Assertions.assertEquals("minion-27", this.set[27].minionId)
        }

        confirmVerified(
            scenariosKeeper,
            feedbackProducer,
            directiveProducer,
            minionsCreationPreparationDirectiveProcessor
        )
    }
}