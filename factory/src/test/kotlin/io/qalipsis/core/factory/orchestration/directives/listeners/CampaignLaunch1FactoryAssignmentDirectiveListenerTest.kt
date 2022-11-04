/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.orchestration.directives.listeners

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class CampaignLaunch1FactoryAssignmentDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var campaignLifeCycleAware1: CampaignLifeCycleAware

    @RelaxedMockK
    private lateinit var campaignLifeCycleAware2: CampaignLifeCycleAware

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    private val campaignLifeCycleAwares: Collection<CampaignLifeCycleAware> by lazy {
        listOf(campaignLifeCycleAware1, campaignLifeCycleAware2)
    }

    @InjectMockKs
    private lateinit var processor: CampaignLaunch1FactoryAssignmentDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept factory assignment directive`() {
        val directive = FactoryAssignmentDirective(
            "my-campaign",
            listOf(
                FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4")),
            ),
            relaxedMockk(),
            channel = "broadcast"
        )

        assertTrue(processor.accept(directive))
    }

    @Test
    @Timeout(1)
    fun `should not accept not factory assignment directive`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    fun `should process the directive and confirm when all is right`() = testCoroutineDispatcher.runTest {
        val directive = FactoryAssignmentDirective(
            "my-campaign",
            listOf(
                FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4")),
            ),
            runningCampaign = RunningCampaign(
                key = "",
                speedFactor = 12.0,
                startOffsetMs = 1212,
                hardTimeout = true,
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        123,
                        RegularExecutionProfileConfiguration(123, 3534)
                    ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                )
            ).apply {
                broadcastChannel = "broadcast-channel"
                feedbackChannel = "feedback-channel"
            },
            channel = "broadcast"
        )

        // when
        processor.notify(directive)

        // then
        val expectedCampaign = Campaign(
            campaignKey = "my-campaign",
            broadcastChannel = "broadcast-channel",
            feedbackChannel = "feedback-channel",
            speedFactor = 12.0,
            startOffsetMs = 1212,
            hardTimeout = true,
            timeout = Instant.MAX,
            scenarios = mapOf(
                "scenario-1" to ScenarioConfiguration(
                    123,
                    RegularExecutionProfileConfiguration(123, 3534)
                ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
            ),
            assignments = listOf(
                FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
            )
        )
        coVerifyOrder {
            campaignLifeCycleAware1.init(expectedCampaign)
            campaignLifeCycleAware2.init(expectedCampaign)
            factoryChannel.publishFeedback(
                FactoryAssignmentFeedback(campaignKey = "my-campaign", status = FeedbackStatus.IN_PROGRESS)
            )
            minionAssignmentKeeper.assignFactoryDags(
                "my-campaign",
                listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
                )
            )
            factoryChannel.publishFeedback(
                FactoryAssignmentFeedback(campaignKey = "my-campaign", status = FeedbackStatus.COMPLETED)
            )
        }
        confirmVerified(factoryChannel, minionAssignmentKeeper, campaignLifeCycleAware1, campaignLifeCycleAware2)
    }

    @Test
    fun `should process the directive and fails when there is an exception`() =
        testCoroutineDispatcher.runTest {
            val directive = FactoryAssignmentDirective(
                "my-campaign",
                listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4")),
                ),
                runningCampaign = RunningCampaign(
                    key = "",
                    speedFactor = 12.0,
                    startOffsetMs = 1212,
                    hardTimeout = true,
                    scenarios = mapOf(
                        "scenario-1" to ScenarioConfiguration(
                            123,
                            RegularExecutionProfileConfiguration(123, 3534)
                        ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                    )
                ).apply {
                    broadcastChannel = "broadcast-channel"
                    feedbackChannel = "feedback-channel"
                    timeoutSinceEpoch = 17253L
                },
                channel = "broadcast"
            )
            coEvery { campaignLifeCycleAware1.init(any()) } throws RuntimeException("A problem occurred")

            // when
            processor.notify(directive)

            // then
            val expectedCampaign = Campaign(
                campaignKey = "my-campaign",
                broadcastChannel = "broadcast-channel",
                feedbackChannel = "feedback-channel",
                speedFactor = 12.0,
                startOffsetMs = 1212,
                hardTimeout = true,
                timeout = Instant.ofEpochSecond(17253L),
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        123,
                        RegularExecutionProfileConfiguration(123, 3534)
                    ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                ),
                assignments = listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
                )
            )
            coVerifyOrder {
                campaignLifeCycleAware1.init(expectedCampaign)
                factoryChannel.publishFeedback(
                    FactoryAssignmentFeedback(
                        campaignKey = "my-campaign",
                        status = FeedbackStatus.FAILED,
                        errorMessage = "A problem occurred"
                    )
                )
            }
            confirmVerified(factoryChannel, minionAssignmentKeeper, campaignLifeCycleAware1, campaignLifeCycleAware2)
        }
}