/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.serialization

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isEqualToWithGivenProperties
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.feedbacks.FactoryAssignmentFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "streaming.serialization", value = "json")
internal class JsonRecordDistributionSerializerIntegrationTest {

    @Inject
    lateinit var serializer: JsonRecordDistributionSerializer

    @Test
    fun `should serialize record`() {
        // when
        val serializedRecord =
            """{"value":"{\"campaignKey\":\"test-tenant-1:cm7eyk68z00040p71uucrxv87\",\"status\":\"FAILED\",\"nodeId\":\"test-tenant-1:cm7eyk68z00040p71uucrxv87_my-new-scenario_default_1\",\"tenant\":\"test-tenant-1\",\"error\":\"A factory could not be started successfully\"}","type":"io.qalipsis.core.feedbacks.FactoryAssignmentFeedback","serializer":"json-debug"}"""

        val serialized = serializer.serialize(
            FactoryAssignmentFeedback(
                campaignKey = "test-tenant-1:cm7eyk68z00040p71uucrxv87",
                errorMessage = "A factory could not be started successfully",
                status = FeedbackStatus.FAILED
            ).also {
                it.tenant = "test-tenant-1"
                it.nodeId = "test-tenant-1:cm7eyk68z00040p71uucrxv87_my-new-scenario_default_1"
            }
        )

        // then
        assertThat(serialized.decodeToString()).isEqualTo(serializedRecord)
    }

    @Test
    fun `should deserialize record`() {
        // given
        val serializedRecord =
            """{"value":"{\"campaignKey\":\"test-tenant-1:cm7eyk68z00040p71uucrxv87\",\"tenant\":\"test-tenant-1\",\"nodeId\":\"test-tenant-1:cm7eyk68z00040p71uucrxv87_my-new-scenario_default_1\",\"status\":\"FAILED\",\"errorMessage\":\"A factory could not be started successfully\"}","type":"io.qalipsis.core.feedbacks.FactoryAssignmentFeedback","serializer":"json-debug"}""".encodeToByteArray()

        // when
        val result = serializer.deserialize<Feedback>(serializedRecord)

        // then
        assertThat(result).isEqualToWithGivenProperties(
            FactoryAssignmentFeedback(
                campaignKey = "test-tenant-1:cm7eyk68z00040p71uucrxv87",
                errorMessage = "A factory could not be started successfully",
                status = FeedbackStatus.FAILED
            ).also {
                it.tenant = "test-tenant-1"
                it.nodeId = "test-tenant-1:cm7eyk68z00040p71uucrxv87_my-new-scenario_default_1"
            }
        )

    }
}