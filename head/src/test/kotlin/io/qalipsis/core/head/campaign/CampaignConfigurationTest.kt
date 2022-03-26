package io.qalipsis.core.head.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.key
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.FactoryConfiguration
import org.junit.jupiter.api.Test

internal class CampaignConfigurationTest {

    @Test
    internal fun `should unassign the scenario from the factory`() {
        // given
        val campaign = CampaignConfiguration(
            id = "my-campaign",
        )
        campaign.factories += mapOf(
            "factory-1" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-1" to listOf("dag-1", "dag-2", "dag-3"),
                    "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
                )
            ),
            "factory-2" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
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
                        "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
                    )
                )
            )
            key("factory-2").isDataClassEqualTo(
                FactoryConfiguration(
                    "",
                    mutableMapOf(
                        "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
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
                        "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
                    )
                )
            )
        }
    }


    @Test
    internal fun `should unassign the factory`() {
        // given
        val campaign = CampaignConfiguration(
            id = "my-campaign"
        )
        campaign.factories += mapOf(
            "factory-1" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-1" to listOf("dag-1", "dag-2", "dag-3"),
                    "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
                )
            ),
            "factory-2" to FactoryConfiguration(
                "",
                mutableMapOf(
                    "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
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
                        "scenario-2" to listOf("dag-a", "dag-b", "dag-c")
                    )
                )
            )
        }
    }
}