package io.qalipsis.core.heads.campaigns

import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.cross.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedbackScenario
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.utils.setProperty
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

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
    lateinit var headScenarioRepository: HeadScenarioRepository

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @Test
    @Timeout(3)
    internal fun `should start a campaign with the specified name when the factory registers`() = runBlockingTest {
        // given
        val campaignAutoStarter = CampaignAutoStarter(
            feedbackConsumer, campaignManager, headScenarioRepository, eventsLogger, "my-campaign"
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
        coEvery { feedbackConsumer.onReceive(any()) } coAnswers {
            launch {
                (firstArg() as suspend (Feedback) -> Unit).invoke(FactoryRegistrationFeedback(scenarios))
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
    internal fun `should not start a campaign when other feedback is received`() = runBlockingTest {
        // given
        val campaignAutoStarter = CampaignAutoStarter(
            feedbackConsumer, campaignManager, headScenarioRepository, eventsLogger, "my-campaign"
        )
        val countDown = SuspendedCountLatch(1)
        coEvery { feedbackConsumer.onReceive(any()) } coAnswers {
            launch {
                (firstArg() as suspend (Feedback) -> Unit).invoke(relaxedMockk())
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
    internal fun `should stop the event logger and release the larch only when all the campaign end`() =
        runBlockingTest {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                feedbackConsumer, campaignManager, headScenarioRepository, eventsLogger, "my-campaign"
            )
            val runningScenariosLatch = spyk(SuspendedCountLatch(2))
            campaignAutoStarter.setProperty("runningScenariosLatch", runningScenariosLatch)
            coEvery { feedbackConsumer.onReceive(any()) } coAnswers {
                launch {
                    repeat(2) { index ->
                        (firstArg() as suspend (Feedback) -> Unit).invoke(
                            EndOfCampaignFeedback(
                                "my campaign",
                                "my scenario $index"
                            )
                        )
                    }
                }
            }

            // when
            campaignAutoStarter.init()

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
}
