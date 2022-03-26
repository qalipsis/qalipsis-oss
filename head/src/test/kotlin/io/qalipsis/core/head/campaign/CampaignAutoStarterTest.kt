package io.qalipsis.core.head.campaign

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.FactoryShutdownDirective
import io.qalipsis.core.factory.communication.HeadChannel
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.campaign.catadioptre.campaign
import io.qalipsis.core.head.campaign.catadioptre.campaignLatch
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.coMeasureTime
import jakarta.inject.Provider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignAutoStarterTest {

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var campaignManagerProvider: Provider<CampaignManager>

    @RelaxedMockK
    private lateinit var factoryService: FactoryService

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @RelaxedMockK
    private lateinit var headChannel: HeadChannel

    private val autostartConfiguration = object : AutostartCampaignConfiguration {
        override val id: String = "my-campaign"
        override val requiredFactories: Int = 1
        override val triggerOffset: Duration = Duration.ofMillis(456)
        override val minionsCountPerScenario: Int = 0
        override val minionsFactor: Double = 1.87
        override val speedFactor: Double = 54.87
        override val startOffset: Duration = Duration.ofMillis(12367)
    }

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @BeforeEach
    internal fun setUp() {
        every { campaignManagerProvider.get() } returns campaignManager
    }

    @Test
    @Timeout(3)
    internal fun `should start a campaign with the specified name when enough factories are registered`() =
        testCoroutineDispatcher.run {
            // given
            every { campaignManagerProvider.get() } returns campaignManager
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManagerProvider,
                campaignReportStateKeeper,
                object : AutostartCampaignConfiguration {
                    override val id: String = "my-campaign"
                    override val requiredFactories: Int = 2
                    override val triggerOffset: Duration = Duration.ofMillis(456)
                    override val minionsCountPerScenario: Int = 0
                    override val minionsFactor: Double = 1.87
                    override val speedFactor: Double = 54.87
                    override val startOffset: Duration = Duration.ofMillis(12367)
                },
                headChannel
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-3"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(setOf("scenario-1", "scenario-2", "scenario-3")) } returns
                    listOf(scenario1, scenario2)

            // when
            campaignAutoStarter.notify(relaxedMockk<HandshakeRequest> {
                every { scenarios } returns listOf(relaxedMockk { every { id } returns "scenario-1" })
            })
            campaignAutoStarter.notify(relaxedMockk<HandshakeRequest> {
                every { scenarios } returns listOf(
                    relaxedMockk { every { id } returns "scenario-2" },
                    relaxedMockk { every { id } returns "scenario-3" })
            })
            campaignAutoStarter.notify(Heartbeat("node-1", Instant.now()))

            // then
            coVerifyNever { campaignManager.start(any()) }

            // when
            val elapsed = coMeasureTime {
                campaignAutoStarter.notify(Heartbeat("node-2", Instant.now()))
            }

            // then
            assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(420)) // Should have waited triggerOffset.
            coVerifyOnce {
                campaignManager.start(
                    CampaignConfiguration(
                        id = "my-campaign",
                        speedFactor = 54.87,
                        startOffsetMs = 12367,
                        scenarios = mapOf(
                            "scenario-1" to ScenarioConfiguration(minionsCount = 18),
                            "scenario-3" to ScenarioConfiguration(minionsCount = 187),
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
            every { campaignManagerProvider.get() } returns campaignManager
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManagerProvider,
                campaignReportStateKeeper,
                autostartConfiguration,
                headChannel
            )

            // when
            campaignAutoStarter.notify(relaxedMockk<HandshakeRequest> { every { scenarios } returns emptyList() })
            campaignAutoStarter.notify(Heartbeat("node-1", Instant.now()))

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
    @Timeout(2)
    internal fun `should not start when no handshake request was notified`() =
        testCoroutineDispatcher.run {
            // given
            every { campaignManagerProvider.get() } returns campaignManager
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManagerProvider,
                campaignReportStateKeeper,
                autostartConfiguration,
                headChannel
            )

            // when
            assertThrows<TimeoutCancellationException> {
                withTimeout(500) {
                    campaignAutoStarter.notify(Heartbeat("node-1", Instant.now()))
                }
            }
        }

    @Test
    @Timeout(1)
    internal fun `should release the latch when the campaign is complete with success`() =
        testCoroutineDispatcher.run {
            // given
            every { campaignManagerProvider.get() } returns campaignManager
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManagerProvider,
                campaignReportStateKeeper,
                autostartConfiguration,
                headChannel
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-2"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(setOf("scenario-1", "scenario-2")) } returns listOf(
                scenario1,
                scenario2
            )
            campaignAutoStarter.notify(relaxedMockk<HandshakeRequest> {
                every { scenarios } returns listOf(
                    relaxedMockk { every { id } returns "scenario-1" },
                    relaxedMockk { every { id } returns "scenario-2" }
                )
            })
            campaignAutoStarter.notify(Heartbeat("node-1", Instant.now()))
            campaignAutoStarter.campaign(relaxedMockk {
                every { broadcastChannel } returns "the-broadcast-channel"
                every { factories } returns mutableMapOf(
                    "factory-1" to relaxedMockk { every { unicastChannel } returns "channel-factory-1" },
                    "factory-2" to relaxedMockk { every { unicastChannel } returns "channel-factory-2" }
                )
            })
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isTrue()

            // when
            campaignAutoStarter.completeCampaign(
                CompleteCampaignDirective(
                    campaignId = "my-campaign",
                    isSuccessful = true,
                    "", "the-broadcast-channel"
                )
            )

            // then
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isFalse()
            coVerifyOnce {
                headChannel.publishDirective(FactoryShutdownDirective("channel-factory-1"))
                headChannel.publishDirective(FactoryShutdownDirective("channel-factory-2"))
            }

            campaignAutoStarter.join()
        }

    @Test
    @Timeout(1)
    internal fun `should release the latch when the campaign is complete with failure`() =
        testCoroutineDispatcher.run {
            // given
            every { campaignManagerProvider.get() } returns campaignManager
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManagerProvider,
                campaignReportStateKeeper,
                autostartConfiguration,
                headChannel
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-2"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(setOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2)
            campaignAutoStarter.notify(relaxedMockk<HandshakeRequest> {
                every { scenarios } returns listOf(
                    relaxedMockk { every { id } returns "scenario-1" },
                    relaxedMockk { every { id } returns "scenario-2" }
                )
            })
            campaignAutoStarter.notify(Heartbeat("node-1", Instant.now()))
            campaignAutoStarter.campaign(relaxedMockk {
                every { broadcastChannel } returns "the-broadcast-channel"
                every { factories } returns mutableMapOf(
                    "factory-1" to relaxedMockk { every { unicastChannel } returns "channel-factory-1" },
                    "factory-2" to relaxedMockk { every { unicastChannel } returns "channel-factory-2" }
                )
            })
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isTrue()

            // when
            campaignAutoStarter.completeCampaign(
                CompleteCampaignDirective(
                    campaignId = "my-campaign",
                    isSuccessful = false,
                    "There is an error", "the-broadcast-channel"
                )
            )

            // then
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isFalse()
            coVerifyOnce {
                headChannel.publishDirective(FactoryShutdownDirective("channel-factory-1"))
                headChannel.publishDirective(FactoryShutdownDirective("channel-factory-2"))
            }

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
            every { campaignManagerProvider.get() } returns campaignManager
            val campaignAutoStarter = CampaignAutoStarter(
                factoryService,
                campaignManagerProvider,
                campaignReportStateKeeper,
                autostartConfiguration,
                headChannel
            )
            val scenario1 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-1"
                every { minionsCount } returns 10
            }
            val scenario2 = relaxedMockk<ScenarioSummary> {
                every { id } returns "scenario-2"
                every { minionsCount } returns 100
            }
            coEvery { factoryService.getActiveScenarios(setOf("scenario-1", "scenario-2")) } returns
                    listOf(scenario1, scenario2)

            // when
            campaignAutoStarter.notify(relaxedMockk<HandshakeRequest> {
                every { scenarios } returns listOf(
                    relaxedMockk { every { id } returns "scenario-1" },
                    relaxedMockk { every { id } returns "scenario-2" }
                )
            })
            campaignAutoStarter.notify(relaxedMockk<Heartbeat> { every { nodeId } returns "node-1" })

            // then
            assertThat(campaignAutoStarter.campaignLatch().isLocked).isTrue()
            assertThrows<TimeoutCancellationException> {
                withTimeout(100) { campaignAutoStarter.join() }
            }
        }

}
