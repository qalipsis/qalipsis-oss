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

package io.qalipsis.core.directives

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isNotNull
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.executionprofile.CompletionMode.GRACEFUL
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.serialization.SerialFormatRecordSerializer
import jakarta.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
@MicronautTest(startApplication = false)
internal class FactoryApiDirectivesIntegrationTest {

    @Inject
    private lateinit var protoBuf: ProtoBuf

    @Inject
    private lateinit var serializer: SerialFormatRecordSerializer

    @Test
    fun `should encode and decode FactoryAssignmentDirective as directive`() {
        val directive: Directive = FactoryAssignmentDirective(
            "my-campaign",
            listOf(
                FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4")),
            ),
            runningCampaign = RunningCampaign(
                tenant = "my-tenant", key = "my-campaign", scenarios = mapOf(
                    "scenario-1" to ScenarioConfiguration(
                        26,
                        StageExecutionProfileConfiguration(
                            GRACEFUL,
                            listOf(
                                Stage(
                                    minionsCount = 12,
                                    rampUpDurationMs = 2000,
                                    totalDurationMs = 3000,
                                    resolutionMs = 500
                                ),
                                Stage(
                                    minionsCount = 14,
                                    rampUpDurationMs = 1500,
                                    totalDurationMs = 2000,
                                    resolutionMs = 400
                                )
                            )
                        )
                    )
                )
            ).apply {
                broadcastChannel = "broadcast-channel"
                feedbackChannel = "feedback-channel"
            },
            channel = "broadcast"
        )
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(directive))).isNotNull().isDataClassEqualTo(directive)

    }

    @Test
    fun `should encode and decode ScenarioWarmUpDirective as directive`() {
        val directive: Directive = ScenarioWarmUpDirective("my-campaign", "my-scenario-1", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(directive))).isNotNull().isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode CampaignScenarioShutdownDirective as directive`() {
        val directive: Directive = CampaignScenarioShutdownDirective("my-campaign", "my-scenario-1", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
    }

    @Test
    fun `should encode and decode CampaignShutdownDirective as directive`() {
        val directive: Directive = CampaignShutdownDirective("my-campaign", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
    }

    @Test
    fun `should encode and decode CompleteCampaignDirective as directive`() {
        val directive: Directive =
            CompleteCampaignDirective("my-campaign", true, "the completion message", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
    }

    @Test
    fun `should encode and decode CampaignAbortDirective as directive`() {
        val directive: Directive = CampaignAbortDirective(
            campaignKey = "my-campaign",
            channel = "the-channel",
            scenarioNames = listOf("my-scenario-1", "my-scenario-2"),
            abortRunningCampaign = AbortRunningCampaign()
        )
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
    }
}