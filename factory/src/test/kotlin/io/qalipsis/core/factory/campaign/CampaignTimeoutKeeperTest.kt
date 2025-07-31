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

package io.qalipsis.core.factory.campaign

import assertk.assertThat
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.factory.campaign.catadioptre.hardTimerJob
import io.qalipsis.core.factory.campaign.catadioptre.softTimerJob
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.feedbacks.CampaignTimeoutFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class CampaignTimeoutKeeperTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @Test
    fun `should publish the timeout feedback with the right params when a hard timeout is specified`() =
        testDispatcherProvider.runTest {
            val campaign = Campaign(
                campaignKey = "my-campaign",
                broadcastChannel = "broadcast-channel",
                feedbackChannel = "feedback-channel",
                speedFactor = 12.0,
                startOffsetMs = 1212,
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        123,
                        RegularExecutionProfileConfiguration(123, 3534)
                    ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                ),
                softTimeout = null,
                hardTimeout = Instant.now().plusSeconds(1),
                assignments = listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
                )
            )
            val campaignTimeoutKeeper = CampaignTimeoutKeeper(factoryChannel, this)
            val latch = Latch(true)
            coEvery { factoryChannel.publishFeedback(any<CampaignTimeoutFeedback>()) } coAnswers { latch.release() }

            // when
            campaignTimeoutKeeper.init(campaign)
            latch.await()

            //then
            coVerifyOrder {
                factoryChannel.publishFeedback(
                    CampaignTimeoutFeedback(
                        campaignKey = "my-campaign",
                        status = FeedbackStatus.COMPLETED,
                        hard = true,
                        errorMessage = "The hard timeout was reached"
                    )
                )
            }
            confirmVerified(factoryChannel)
        }


    @Test
    fun `should publish the timeout feedback with the right params when a soft timeout is specified`() =
        testDispatcherProvider.runTest {
            val campaign = Campaign(
                campaignKey = "my-campaign",
                broadcastChannel = "broadcast-channel",
                feedbackChannel = "feedback-channel",
                speedFactor = 12.0,
                startOffsetMs = 1212,
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        123,
                        RegularExecutionProfileConfiguration(123, 3534)
                    ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                ),
                hardTimeout = null,
                softTimeout = Instant.now().plusSeconds(1),
                assignments = listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
                )
            )
            val campaignTimeoutKeeper = CampaignTimeoutKeeper(factoryChannel, this)
            val latch = Latch(true)
            coEvery { factoryChannel.publishFeedback(any<CampaignTimeoutFeedback>()) } coAnswers { latch.release() }

            // when
            campaignTimeoutKeeper.init(campaign)
            latch.await()

            //then
            coVerifyOrder {
                factoryChannel.publishFeedback(
                    CampaignTimeoutFeedback(
                        campaignKey = "my-campaign",
                        status = FeedbackStatus.COMPLETED,
                        hard = false,
                        errorMessage = "The soft timeout was reached"
                    )
                )
            }
            confirmVerified(factoryChannel)
        }

    @Test
    fun `should not publish any timeout feedback when no timeout is specified`() =
        testDispatcherProvider.runTest {
            val campaign = Campaign(
                campaignKey = "my-campaign",
                broadcastChannel = "broadcast-channel",
                feedbackChannel = "feedback-channel",
                speedFactor = 12.0,
                startOffsetMs = 1212,
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        123,
                        RegularExecutionProfileConfiguration(123, 3534)
                    ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                ),
                hardTimeout = null,
                softTimeout = null,
                assignments = listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
                )
            )
            val campaignTimeoutKeeper = CampaignTimeoutKeeper(factoryChannel, this)

            // when
            campaignTimeoutKeeper.init(campaign)

            //then
            coVerifyNever {
                factoryChannel.publishFeedback(any())
            }
            assertThat(campaignTimeoutKeeper.softTimerJob()).isNull()
            assertThat(campaignTimeoutKeeper.hardTimerJob()).isNull()
        }

    @Test
    fun `should publish both timeout feedback with the right params when both soft and hard timeouts are specified`() =
        testDispatcherProvider.runTest {
            val campaign = Campaign(
                campaignKey = "my-campaign",
                broadcastChannel = "broadcast-channel",
                feedbackChannel = "feedback-channel",
                speedFactor = 12.0,
                startOffsetMs = 1212,
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        123,
                        RegularExecutionProfileConfiguration(123, 3534)
                    ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                ),
                hardTimeout = null,
                softTimeout = Instant.now().plusSeconds(1),
                assignments = listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
                )
            )
            val campaign2 = Campaign(
                campaignKey = "my-campaign2",
                broadcastChannel = "broadcast-channel2",
                feedbackChannel = "feedback-channel2",
                speedFactor = 12.0,
                startOffsetMs = 1212,
                scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        123,
                        RegularExecutionProfileConfiguration(123, 3534)
                    ), "scenario-2" to ScenarioConfiguration(5432, DefaultExecutionProfileConfiguration())
                ),
                softTimeout = null,
                hardTimeout = Instant.now().plusSeconds(1),
                assignments = listOf(
                    FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                    FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4"))
                )
            )
            val campaignTimeoutKeeper = CampaignTimeoutKeeper(factoryChannel, this)
            val countLatch = SuspendedCountLatch(2)
            coEvery { factoryChannel.publishFeedback(any<CampaignTimeoutFeedback>()) } coAnswers { countLatch.decrement() }

            // when
            campaignTimeoutKeeper.init(campaign)
            campaignTimeoutKeeper.init(campaign2)

            //then
            countLatch.await()
            coVerifyOnce {
                factoryChannel.publishFeedback(
                    CampaignTimeoutFeedback(
                        campaignKey = "my-campaign",
                        status = FeedbackStatus.COMPLETED,
                        hard = false,
                        errorMessage = "The soft timeout was reached"
                    )
                )
                factoryChannel.publishFeedback(
                    CampaignTimeoutFeedback(
                        campaignKey = "my-campaign2",
                        status = FeedbackStatus.COMPLETED,
                        hard = true,
                        errorMessage = "The hard timeout was reached"
                    )
                )
            }
            confirmVerified(factoryChannel)
        }

}