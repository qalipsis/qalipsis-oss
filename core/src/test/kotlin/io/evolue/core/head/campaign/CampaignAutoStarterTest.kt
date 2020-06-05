package io.evolue.core.head.campaign

import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedback
import io.evolue.core.cross.driving.feedback.FactoryRegistrationFeedbackScenario
import io.evolue.core.cross.driving.feedback.Feedback
import io.evolue.core.cross.driving.feedback.FeedbackConsumer
import io.evolue.test.mockk.coVerifyNever
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.mockk.relaxedMockk
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class CampaignAutoStarterTest {

    @RelaxedMockK
    lateinit var feedbackConsumer: FeedbackConsumer

    @RelaxedMockK
    lateinit var campaignManager: CampaignManager

    @Test
    internal fun `should start a campaign when factory registers`() {
        // given
        val countDown = CountDownLatch(1)
        val scenarios: List<FactoryRegistrationFeedbackScenario> = listOf(
            relaxedMockk {
                io.mockk.every { id } returns "scen-1"
            },
            relaxedMockk {
                io.mockk.every { id } returns "scen-2"
            }
        )
        coEvery { feedbackConsumer.subscribe() } returns flowOf(FactoryRegistrationFeedback(scenarios)).also {
            countDown.countDown()
        }

        // when
        CampaignAutoStarter(feedbackConsumer, campaignManager)

        // then
        countDown.await()
        coVerifyOnce { campaignManager.start(not(isNull()), eq(listOf("scen-1", "scen-2")), any()) }
    }

    @Test
    internal fun `should not start a campaign when other feedback is received`() {
        // given
        val countDown = CountDownLatch(1)
        coEvery { feedbackConsumer.subscribe() } returns flowOf(relaxedMockk<Feedback>()).also {
            countDown.countDown()
        }

        // when
        CampaignAutoStarter(feedbackConsumer, campaignManager)

        // then
        countDown.await()
        coVerifyNever { campaignManager.start(any(), any(), any()) }
    }
}
