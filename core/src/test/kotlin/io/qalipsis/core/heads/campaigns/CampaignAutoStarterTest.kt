package io.qalipsis.core.heads.campaigns

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackConsumer
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedback
import io.qalipsis.core.cross.feedbacks.FactoryRegistrationFeedbackScenario
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
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

    @InjectMockKs
    lateinit var campaignAutoStarter: CampaignAutoStarter

    @Test
    @Timeout(3)
    internal fun `should start a campaign when factory registers`() = runBlockingTest {
        // given
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
        coVerifyOnce { campaignManager.start(not(isNull()), eq(listOf("scen-1", "scen-2")), any()) }
    }

    @Test
    @Timeout(1)
    internal fun `should not start a campaign when other feedback is received`() = runBlockingTest {
        // given
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
        coVerifyNever { campaignManager.start(any(), any(), any()) }
    }
}
