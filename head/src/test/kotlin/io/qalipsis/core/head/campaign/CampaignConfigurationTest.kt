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

package io.qalipsis.core.head.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.key
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import org.junit.jupiter.api.Test

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
}