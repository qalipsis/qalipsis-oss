package io.qalipsis.core.head.campaign

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.head.campaign.catadioptre.campaignLatch
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignAutoStarterTest {

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    private val autostartConfiguration = object : AutostartCampaignConfiguration {
        override val id: String = "my-campaign"
        override val minionsCountPerScenario: Int = 0
        override val minionsFactor: Double = 1.87
        override val speedFactor: Double = 54.87
        override val startOffsetMs: Long = 12367
        override val scenarios: List<ScenarioId> = listOf("scenario-1", "scenario-2")
    }

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should start a campaign with the specified name`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManager,
                campaignReportStateKeeper,
                autostartConfiguration
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-2"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(listOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2)

            // when
            campaignAutoStarter.trigger(listOf("scenario-1", "scenario-2"))

            // then
            coVerifyOnce {
                campaignManager.start(
                    eq(
                        CampaignConfiguration(
                            id = "my-campaign",
                            speedFactor = 54.87,
                            startOffsetMs = 12367,
                            broadcastChannel = "",
                            scenarios = mapOf(
                                "scenario-1" to ScenarioConfiguration(minionsCount = 18),
                                "scenario-2" to ScenarioConfiguration(minionsCount = 187),
                            ),
                            factories = mutableMapOf()
                        )
                    )
                )
            }
        }


    @Test
    @Timeout(1)
    internal fun `should release the starter when there is no scenario to execute`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManager,
                campaignReportStateKeeper,
                autostartConfiguration
            )

            // when
            campaignAutoStarter.trigger(emptyList())

            // then
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isFalse()
            coVerify {
                campaignReportStateKeeper.abort("my-campaign")
            }

            // when
            val exception = assertThrows<RuntimeException> {
                campaignAutoStarter.join()
            }

            // then
            assertThat(exception.message).isEqualTo("No executable scenario was found")
        }

    @Test
    @Timeout(1)
    internal fun `should release the latch when the campaign is complete with success`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManager,
                campaignReportStateKeeper,
                autostartConfiguration
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-2"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(listOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2)
            campaignAutoStarter.trigger(listOf("scenario-1", "scenario-2"))
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isTrue()

            // when
            campaignAutoStarter.process(
                CompleteCampaignDirective(
                    campaignId = "my-campaign",
                    isSuccessful = true,
                    "", "", ""
                )
            )

            // then
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isFalse()
            campaignAutoStarter.join()
        }

    @Test
    @Timeout(1)
    internal fun `should release the latch when the campaign is complete with failure`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManager,
                campaignReportStateKeeper,
                autostartConfiguration
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-2"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(listOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2)
            campaignAutoStarter.trigger(listOf("scenario-1", "scenario-2"))
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isTrue()

            // when
            campaignAutoStarter.process(
                CompleteCampaignDirective(
                    campaignId = "my-campaign",
                    isSuccessful = false,
                    "There is an error", "", ""
                )
            )

            // then
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isFalse()

            // when
            val exception = assertThrows<RuntimeException> {
                campaignAutoStarter.join()
            }

            // then
            assertThat(exception.message).isEqualTo("There is an error")
        }

    @Test
    @Timeout(1)
    internal fun `should not release the latch until the campaign is complete`() =
        testCoroutineDispatcher.run {
            // given
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManager,
                campaignReportStateKeeper,
                autostartConfiguration
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-2"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(listOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2)

            // when
            campaignAutoStarter.trigger(listOf("scenario-1", "scenario-2"))

            // then
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isTrue()
            assertThrows<TimeoutCancellationException> {
                withTimeout(100) { campaignAutoStarter.join() }
            }
        }

}
