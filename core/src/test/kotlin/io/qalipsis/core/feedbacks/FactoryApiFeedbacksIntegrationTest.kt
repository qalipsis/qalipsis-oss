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

package io.qalipsis.core.feedbacks

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isNotNull
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.feedbacks.FeedbackStatus.COMPLETED
import io.qalipsis.core.serialization.SerialFormatRecordSerializer
import jakarta.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
@MicronautTest(startApplication = false)
class FactoryApiFeedbacksIntegrationTest {

    @Inject
    private lateinit var protoBuf: ProtoBuf

    @Inject
    private lateinit var serializer: SerialFormatRecordSerializer

    @Test
    fun `should encode and decode FactoryAssignmentFeedback as feedback`() {
        val feedback: Feedback = FactoryAssignmentFeedback("my-campaign", COMPLETED, "The message").apply {
            nodeId = "the node"
            tenant = "the tenant"
        }

        // when + then
        assertThat(protoBuf.decodeFromByteArray<Feedback>(protoBuf.encodeToByteArray(feedback))).isDataClassEqualTo(
            feedback
        )

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(feedback))).isNotNull()
            .isDataClassEqualTo(feedback)
    }

    @Test
    fun `should encode and decode ScenarioWarmUpFeedback as feedback`() {
        val feedback: Feedback = ScenarioWarmUpFeedback("my-campaign", "my-scenario", COMPLETED, "The message").apply {
            nodeId = "the node"
            tenant = "the tenant"
        }

        // when + then
        assertThat(protoBuf.decodeFromByteArray<Feedback>(protoBuf.encodeToByteArray(feedback))).isDataClassEqualTo(
            feedback
        )

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(feedback))).isNotNull()
            .isDataClassEqualTo(feedback)
    }

    @Test
    fun `should encode and decode EndOfCampaignScenarioFeedback as feedback`() {
        val feedback: Feedback = EndOfCampaignScenarioFeedback("my-campaign", "my-scenario", COMPLETED, "The message").apply {
            nodeId = "the node"
            tenant = "the tenant"
        }

        // when + then
        assertThat(protoBuf.decodeFromByteArray<Feedback>(protoBuf.encodeToByteArray(feedback))).isDataClassEqualTo(
            feedback
        )

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(feedback))).isNotNull()
            .isDataClassEqualTo(feedback)
    }

    @Test
    fun `should encode and decode EndOfCampaignFeedback as feedback`() {
        val feedback: Feedback = EndOfCampaignFeedback("my-campaign", COMPLETED, "The message").apply {
            nodeId = "the node"
            tenant = "the tenant"
        }

        // when + then
        assertThat(protoBuf.decodeFromByteArray<Feedback>(protoBuf.encodeToByteArray(feedback))).isDataClassEqualTo(
            feedback
        )

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(feedback))).isNotNull()
            .isDataClassEqualTo(feedback)
    }

    @Test
    fun `should encode and decode CampaignScenarioShutdownFeedback as feedback`() {
        val feedback: Feedback = CampaignScenarioShutdownFeedback("my-campaign", "my-scenario", COMPLETED, "The message").apply {
            nodeId = "the node"
            tenant = "the tenant"
        }

        // when + then
        assertThat(protoBuf.decodeFromByteArray<Feedback>(protoBuf.encodeToByteArray(feedback))).isDataClassEqualTo(
            feedback
        )

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(feedback))).isNotNull()
            .isDataClassEqualTo(feedback)
    }

    @Test
    fun `should encode and decode CampaignShutdownFeedback as feedback`() {
        val feedback: Feedback = CampaignShutdownFeedback("my-campaign", COMPLETED, "The message").apply {
            nodeId = "the node"
            tenant = "the tenant"
        }

        // when + then
        assertThat(protoBuf.decodeFromByteArray<Feedback>(protoBuf.encodeToByteArray(feedback))).isDataClassEqualTo(
            feedback
        )

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(feedback))).isNotNull()
            .isDataClassEqualTo(feedback)
    }

    @Test
    fun `should encode and decode CampaignAbortFeedback as feedback`() {
        val feedback: Feedback = CampaignShutdownFeedback("my-campaign", COMPLETED, "The message").apply {
            nodeId = "the node"
            tenant = "the tenant"
        }

        // when + then
        assertThat(protoBuf.decodeFromByteArray<Feedback>(protoBuf.encodeToByteArray(feedback))).isDataClassEqualTo(
            feedback
        )

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(feedback))).isNotNull()
            .isDataClassEqualTo(feedback)
    }
}