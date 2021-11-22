package io.qalipsis.core.head.campaign

import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.FeedbackHeadChannel
import io.qalipsis.core.head.campaign.catadioptre.runningScenariosLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignAutoStarterTest {

    @RelaxedMockK
    private lateinit var feedbackHeadChannel: FeedbackHeadChannel

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var campaignStateKeeper: CampaignStateKeeper

    private val configuration = DataCampaignConfiguration(
        id = "my-campaign",
        minionsCountPerScenario = 123123,
        minionsFactor = 1.87,
        speedFactor = 54.87,
        startOffsetMs = 12367,
        scenarios = emptyList(),
    )

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should start a campaign with the specified name when the factory registers`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                feedbackHeadChannel,
                campaignManager,
                campaignStateKeeper,
                configuration,
                this
            )

            // when
            campaignAutoStarter.trigger(listOf("value-1", "value-2"))

            // then
            coVerifyOnce {
                campaignManager.start(
                    eq(
                        DataCampaignConfiguration(
                            id = "my-campaign",
                            minionsCountPerScenario = 123123,
                            minionsFactor = 1.87,
                            speedFactor = 54.87,
                            startOffsetMs = 12367,
                            scenarios = listOf("value-1", "value-2"),
                        )
                    ), any()
                )
            }
        }


    @Test
    @Timeout(1)
    internal fun `should release the starter when there is no scenario to execute`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                feedbackHeadChannel,
                campaignManager,
                campaignStateKeeper,
                configuration,
                this
            )
            val runningScenariosLatch = spyk(SuspendedCountLatch())
            runningScenariosLatch.increment(2)
            campaignAutoStarter.runningScenariosLatch(runningScenariosLatch)

            // when
            campaignAutoStarter.trigger(emptyList())

            // then
            campaignAutoStarter.join()
            coVerify {
                campaignStateKeeper.abort("my-campaign")
                runningScenariosLatch.increment(eq(1L))
                runningScenariosLatch.release()
            }
        }

    @Test
    @Timeout(1)
    internal fun `should stop the event logger and release the latch only when all the campaign end`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                feedbackHeadChannel,
                campaignManager,
                campaignStateKeeper,
                configuration,
                this
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
            }
        }

}
