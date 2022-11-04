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

package io.qalipsis.core.campaigns

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.key
import io.qalipsis.api.executionprofile.CompletionMode.GRACEFUL
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ProgressiveVolumeExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.TimeFrameExecutionProfileConfiguration
import io.qalipsis.core.serialization.SerializationFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class RunningCampaignTest {

    @Test
    internal fun `should unassign the scenario from the factory`() {
        // given
        val campaign = RunningCampaign(key = "my-campaign")
        campaign.factories += mapOf(
            "factory-1" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-1" to FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2", "dag-3")),
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                )
            ),
            "factory-2" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                )
            )
        )

        // when
        campaign.unassignScenarioOfFactory("scenario-1", "factory-1")

        // then
        assertThat("factory-1" in campaign).isTrue()
        assertThat("factory-2" in campaign).isTrue()
        assertThat(campaign.factories).all {
            hasSize(2)
            key("factory-1").isDataClassEqualTo(
                FactoryConfiguration(
                    "",
                    mutableMapOf(
                        "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                    )
                )
            )
            key("factory-2").isDataClassEqualTo(
                FactoryConfiguration(
                    "",
                    mutableMapOf(
                        "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                    )
                )
            )
        }

        // when
        campaign.unassignScenarioOfFactory("scenario-2", "factory-1")

        // then
        assertThat("factory-1" in campaign).isFalse()
        assertThat("factory-2" in campaign).isTrue()
        assertThat(campaign.factories).all {
            hasSize(1)
            key("factory-2").isDataClassEqualTo(
                FactoryConfiguration(
                    "",
                    mutableMapOf(
                        "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                    )
                )
            )
        }
    }


    @Test
    internal fun `should unassign the factory`() {
        // given
        val campaign = RunningCampaign(key = "my-campaign")
        campaign.factories += mapOf(
            "factory-1" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-1" to FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2", "dag-3")),
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                )
            ),
            "factory-2" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                )
            )
        )

        // when
        campaign.unassignFactory("factory-1")

        // then
        assertThat("factory-1" in campaign).isFalse()
        assertThat("factory-2" in campaign).isTrue()
        assertThat(campaign.factories).all {
            hasSize(1)
            key("factory-2").isDataClassEqualTo(
                FactoryConfiguration(
                    "",
                    mutableMapOf(
                        "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                    )
                )
            )
        }
    }

    @Test
    internal fun `should serialize a complete running campaign`() {
        // given
        val campaign = RunningCampaign(
            tenant = "the-tenant",
            key = "my-campaign",
            speedFactor = 123.4,
            startOffsetMs = 23465,
            hardTimeout = true,
            scenarios = mapOf(
                "scenario-1" to ScenarioConfiguration(123, RegularExecutionProfileConfiguration(764, 564)),
                "scenario-2" to ScenarioConfiguration(
                    123,
                    AcceleratingExecutionProfileConfiguration(764, 123.5, 234, 2365)
                ),
                "scenario-3" to ScenarioConfiguration(
                    123,
                    ProgressiveVolumeExecutionProfileConfiguration(764, 123, 234.5, 2365)
                ),
                "scenario-4" to ScenarioConfiguration(
                    123,
                    StageExecutionProfileConfiguration(GRACEFUL, Stage(12, 234, 75464, 12), Stage(75, 4433, 46456, 343))
                ),
                "scenario-5" to ScenarioConfiguration(123, TimeFrameExecutionProfileConfiguration(764, 564)),
                "scenario-6" to ScenarioConfiguration(123, DefaultExecutionProfileConfiguration()),
            )
        ).apply {
            broadcastChannel = "broadcast-channel"
            feedbackChannel = "feedback-channel"
        }
        campaign.factories += mapOf(
            "factory-1" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-1" to FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2", "dag-3")),
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                )
            ),
            "factory-2" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-2" to FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"))
                )
            )
        )

        // when
        val protobuf = SerializationFactory().protobuf()
        val serialized = protobuf.encodeToByteArray(campaign)
        val deserialized = protobuf.decodeFromByteArray<RunningCampaign>(serialized)

        // then
        assertThat(deserialized).isDataClassEqualTo(campaign)
    }
}