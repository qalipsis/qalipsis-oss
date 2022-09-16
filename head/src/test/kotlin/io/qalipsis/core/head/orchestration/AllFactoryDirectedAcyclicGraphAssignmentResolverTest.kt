package io.qalipsis.core.head.orchestration

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.key
import io.mockk.every
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.head.model.Factory
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

internal class AllFactoryDirectedAcyclicGraphAssignmentResolverTest {

    private val resolver = AllFactoryDirectedAcyclicGraphAssignmentResolver()

    @Test
    internal fun `should assign all the scenarios to all the factories`() {
        // given
        val campaign = RunningCampaign(
            key = "my-campaign",
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

}