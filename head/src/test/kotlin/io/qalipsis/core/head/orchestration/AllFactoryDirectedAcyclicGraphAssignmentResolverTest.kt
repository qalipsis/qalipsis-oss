package io.qalipsis.core.head.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.key
import io.mockk.every
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.campaign.ScenarioConfiguration
import io.qalipsis.core.head.model.Factory
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class AllFactoryDirectedAcyclicGraphAssignmentResolverTest {

    private val resolver = AllFactoryDirectedAcyclicGraphAssignmentResolver()

    @Test
    internal fun `should assign all the scenarios to all the factories`() {
        // given
        val campaign = CampaignConfiguration(
            id = "my-campaign",
            broadcastChannel = "",
            scenarios = mapOf(
                "scenario-1" to ScenarioConfiguration(
                    minionsCount = 54
                ),
                "scenario-2" to ScenarioConfiguration(
                    minionsCount = 433,
                )
            )
        )
        val scenarios = listOf(
            ScenarioSummary(
                id = "scenario-1",
                minionsCount = 54,
                directedAcyclicGraphs = listOf(
                    relaxedMockk { every { id } returns "dag-1" },
                    relaxedMockk { every { id } returns "dag-2" },
                    relaxedMockk { every { id } returns "dag-3" }
                )
            ),
            ScenarioSummary(
                id = "scenario-2",
                minionsCount = 433,
                directedAcyclicGraphs = listOf(
                    relaxedMockk { every { id } returns "dag-a" },
                    relaxedMockk { every { id } returns "dag-b" },
                    relaxedMockk { every { id } returns "dag-c" }
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
        assertThat(assignments.rowMap()).all {
            hasSize(2)
            key("factory-1").all {
                hasSize(2)
                key("scenario-1").containsOnly("dag-1", "dag-2", "dag-3")
                key("scenario-2").containsOnly("dag-a", "dag-b", "dag-c")
            }
            key("factory-2").all {
                hasSize(2)
                key("scenario-1").containsOnly("dag-1", "dag-2", "dag-3")
                key("scenario-2").containsOnly("dag-a", "dag-b", "dag-c")
            }
        }
    }

}