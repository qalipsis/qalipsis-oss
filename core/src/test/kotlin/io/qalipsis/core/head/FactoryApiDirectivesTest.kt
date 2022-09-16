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

package io.qalipsis.core.head

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.configuration.AbortRunningCampaign
import io.qalipsis.core.configuration.ProtobufSerializationModuleConfiguration
import io.qalipsis.core.directives.CampaignAbortDirective
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class FactoryApiDirectivesTest {

    private val protoBuf = ProtobufSerializationModuleConfiguration().protobuf()

    @Test
    fun `should encode and decode FactoryAssignmentDirective as directive`() {
        val directive: Directive = FactoryAssignmentDirective(
            "my-campaign",
            listOf(
                FactoryScenarioAssignment("my-scenario-1", listOf("dag-1", "dag-2")),
                FactoryScenarioAssignment("my-scenario-2", listOf("dag-3", "dag-4")),
            ),
            broadcastChannel = "broadcast-channel",
            feedbackChannel = "feedback-channel",
            channel = "broadcast"
        )
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode ScenarioWarmUpDirective as directive`() {
        val directive: Directive = ScenarioWarmUpDirective("my-campaign", "my-scenario-1", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode CampaignScenarioShutdownDirective as directive`() {
        val directive: Directive = CampaignScenarioShutdownDirective("my-campaign", "my-scenario-1", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode CampaignShutdownDirective as directive`() {
        val directive: Directive = CampaignShutdownDirective("my-campaign", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode CompleteCampaignDirective as directive`() {
        val directive: Directive =
            CompleteCampaignDirective("my-campaign", true, "the completion message", "the-channel")
        val serialized = protoBuf.encodeToByteArray(directive)
        val directiveFromSerialization = protoBuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
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
    }
}