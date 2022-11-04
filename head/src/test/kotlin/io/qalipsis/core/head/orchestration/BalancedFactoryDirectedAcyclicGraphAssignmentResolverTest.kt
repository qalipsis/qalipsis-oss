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

package io.qalipsis.core.head.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.key
import io.mockk.every
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class BalancedFactoryDirectedAcyclicGraphAssignmentResolverTest {

    private val resolver = BalancedFactoryDirectedAcyclicGraphAssignmentResolver()

    @Test
    internal fun `should assign all the scenarios to all the factories`() {
        // given
        val campaign = RunningCampaign(
            key = "my-campaign",
            scenarios = emptyMap()
        )
        val scenarios = listOf(
            ScenarioSummary(
                name = "scenario-1",
                minionsCount = 54,
                directedAcyclicGraphs = listOf(
                    relaxedMockk { every { name } returns "dag-1" },
                    relaxedMockk { every { name } returns "dag-2" },
                    relaxedMockk { every { name } returns "dag-3" }
                )
            ),
            ScenarioSummary(
                name = "scenario-2",
                minionsCount = 433,
                directedAcyclicGraphs = listOf(
                    relaxedMockk { every { name } returns "dag-a" },
                    relaxedMockk { every { name } returns "dag-b" },
                    relaxedMockk { every { name } returns "dag-c" }
                )
            )
        )
        val factories = listOf<Factory>(
            relaxedMockk { every { nodeId } returns "factory-1" },
            relaxedMockk { every { nodeId } returns "factory-2" }
        )

        // when
        val assignments = resolver.resolveFactoriesAssignments(campaign, factories, scenarios)

        // then
        val expectedAssignmentScenario1 = FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2", "dag-3"), 27)
        val expectedAssignmentScenario2 =
            FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"), 217)
        assertThat(assignments.rowMap()).all {
            hasSize(2)
            key("factory-1").all {
                hasSize(2)
                key("scenario-1").isDataClassEqualTo(expectedAssignmentScenario1)
                key("scenario-2").isDataClassEqualTo(expectedAssignmentScenario2)
            }
            key("factory-2").all {
                hasSize(2)
                key("scenario-1").isDataClassEqualTo(expectedAssignmentScenario1)
                key("scenario-2").isDataClassEqualTo(expectedAssignmentScenario2)
            }
        }
    }


    @Test
    internal fun `should assign all the scenarios to all the factories considering the provided campaign configuration`() {
        // given
        val campaign = RunningCampaign(
            key = "my-campaign",
            scenarios = mapOf(
                "scenario-1" to ScenarioConfiguration(
                    minionsCount = 68,
                    DefaultExecutionProfileConfiguration()
                ),
                "scenario-2" to ScenarioConfiguration(
                    minionsCount = 123,
                    DefaultExecutionProfileConfiguration()
                )
            )
        )
        val scenarios = listOf(
            ScenarioSummary(
                name = "scenario-1",
                minionsCount = 54,
                directedAcyclicGraphs = listOf(
                    relaxedMockk { every { name } returns "dag-1" },
                    relaxedMockk { every { name } returns "dag-2" },
                    relaxedMockk { every { name } returns "dag-3" }
                )
            ),
            ScenarioSummary(
                name = "scenario-2",
                minionsCount = 433,
                directedAcyclicGraphs = listOf(
                    relaxedMockk { every { name } returns "dag-a" },
                    relaxedMockk { every { name } returns "dag-b" },
                    relaxedMockk { every { name } returns "dag-c" }
                )
            )
        )
        val factories = listOf<Factory>(
            relaxedMockk { every { nodeId } returns "factory-1" },
            relaxedMockk { every { nodeId } returns "factory-2" }
        )

        // when
        val assignments = resolver.resolveFactoriesAssignments(campaign, factories, scenarios)

        // then
        val expectedAssignmentScenario1 = FactoryScenarioAssignment("scenario-1", listOf("dag-1", "dag-2", "dag-3"), 34)
        val expectedAssignmentScenario2 =
            FactoryScenarioAssignment("scenario-2", listOf("dag-a", "dag-b", "dag-c"), 62)
        assertThat(assignments.rowMap()).all {
            hasSize(2)
            key("factory-1").all {
                hasSize(2)
                key("scenario-1").isDataClassEqualTo(expectedAssignmentScenario1)
                key("scenario-2").isDataClassEqualTo(expectedAssignmentScenario2)
            }
            key("factory-2").all {
                hasSize(2)
                key("scenario-1").isDataClassEqualTo(expectedAssignmentScenario1)
                key("scenario-2").isDataClassEqualTo(expectedAssignmentScenario2)
            }
        }
    }
}