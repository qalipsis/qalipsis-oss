package io.qalipsis.core.heads.campaigns

import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.feedbacks.FactoryRegistrationFeedbackScenario
import io.qalipsis.core.heads.campaigns.catadioptre.runningScenariosLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignAutoStarterTest {

    @RelaxedMockK
    lateinit var feedbackConsumer: FeedbackConsumer

    @RelaxedMockK
    lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    lateinit var campaignStateKeeper: CampaignStateKeeper

    @RelaxedMockK
    lateinit var scenarioSummaryRepository: ScenarioSummaryRepository

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should start a campaign with the specified name when the factory registers`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                feedbackConsumer,
                campaignManager,
                campaignStateKeeper,
                scenarioSummaryRepository,
                eventsLogger,
                this,
                "my-campaign"
            )
            val countDown = SuspendedCountLatch(1)
            val scenarios: List<FactoryRegistrationFeedbackScenario> = listOf(
                relaxedMockk {
                    every { id } returns "scen-1"
                },
                relaxedMockk {
                    every { id } returns "scen-2"
                }
            )
            coEvery { feedbackConsumer.onReceive(any(), any()) } coAnswers {
                launch {
                    (secondArg() as suspend (Feedback) -> Unit).invoke(FactoryRegistrationFeedback(scenarios))
                    countDown.decrement()
                }
            }

            // when
            campaignAutoStarter.init()

            // then
            countDown.await()
            coVerifyOnce {
                eventsLogger.start()
                campaignManager.start(eq("my-campaign"), eq(listOf("scen-1", "scen-2")), any())
            }
        }

    @Test
    @Timeout(1)
    internal fun `should not start a campaign when other feedback is received`() = testCoroutineDispatcher.run {
        // given
        val campaignAutoStarter = CampaignAutoStarter(
            feedbackConsumer,
            campaignManager,
            campaignStateKeeper,
            scenarioSummaryRepository,
            eventsLogger,
            this,
            "my-campaign"
        )
        val countDown = SuspendedCountLatch(1)
        coEvery { feedbackConsumer.onReceive(any(), any()) } coAnswers {
            launch {
                (secondArg() as suspend (Feedback) -> Unit).invoke(relaxedMockk())
                countDown.decrement()
            }
        }

        // when
        campaignAutoStarter.init()

        // then
        countDown.await()
        coVerifyNever {
            eventsLogger.start()
            campaignManager.start(any(), any(), any())
        }
    }

    @Test
    @Timeout(1)
    internal fun `should stop the event logger and release the latch only when all the campaign end`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                feedbackConsumer,
                campaignManager,
                campaignStateKeeper,
                scenarioSummaryRepository,
                eventsLogger,
                this,
                "my-campaign"
            )
            val runningScenariosLatch = spyk(SuspendedCountLatch())
            runningScenariosLatch.increment(2)
            campaignAutoStarter.runningScenariosLatch(runningScenariosLatch)

            // when
            repeat(2) { index ->
                campaignAutoStarter.coInvokeInvisible<Unit>(
                    "receivedFeedback",
                    EndOfCampaignFeedback("my campaign", "my scenario $index")
                )
            }

            // then
            campaignAutoStarter.join()
            coVerifyOrder {
                runningScenariosLatch.decrement(eq(1L))
                runningScenariosLatch.decrement(eq(1L))
                // The event logger should only be stopped after all the ends were received.
                eventsLogger.stop()
            }
            confirmVerified(eventsLogger)
        }

    @Test
    @Timeout(1)
    internal fun `should release the starter when there is no scenario to execute`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                feedbackConsumer,
                campaignManager,
                campaignStateKeeper,
                scenarioSummaryRepository,
                eventsLogger,
                this,
                "my-campaign"
            )
            val runningScenariosLatch = spyk(SuspendedCountLatch())
            runningScenariosLatch.increment(2)
            campaignAutoStarter.runningScenariosLatch(runningScenariosLatch)

            // when
            repeat(2) {
                campaignAutoStarter.coInvokeInvisible<Unit>(
                    "receivedFeedback",
                    FactoryRegistrationFeedback(emptyList())
                )
            }

            // then
            campaignAutoStarter.join()
            coVerifyOrder {
                campaignStateKeeper.abort("my-campaign")
                runningScenariosLatch.increment(eq(1L))
                runningScenariosLatch.release()
                // The event logger should only be stopped after all the ends were received.
                eventsLogger.stop()
            }
            confirmVerified(eventsLogger)
        }
}
