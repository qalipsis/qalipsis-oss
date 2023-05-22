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
import io.qalipsis.core.serialization.SerialFormatRecordSerializer
import jakarta.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import java.time.Instant

@ExperimentalSerializationApi
@MicronautTest(startApplication = false)
internal class MinionsApiDirectivesIntegrationTest {

    @Inject
    private lateinit var protobuf: ProtoBuf

    @Inject
    private lateinit var serializer: SerialFormatRecordSerializer

    @Test
    fun `should encode and decode MinionsStartDirective as directive`() {
        val directive: Directive = MinionsStartDirective(
            "my-campaign",
            "scenario-id",
            Instant.now(),
            "the-channel"
        )
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(directive))).isNotNull().isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode MinionsAssignmentDirective as directive`() {
        val directive: Directive = MinionsAssignmentDirective("campaign", "scenario-id")
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(directive))).isNotNull().isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode ScenarioWarmUpDirective as directive`() {
        val directive: Directive = ScenarioWarmUpDirective("campaign-id", "scenario-id", channel = "broadcast")
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(directive))).isNotNull().isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode MinionsShutdownDirective as directive`() {
        val directive: Directive =
            MinionsShutdownDirective("campaign-id", "scenario-id", listOf("minion-1", "minion-2"), "the-channel")
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)

        // when + then
        assertThat(serializer.deserialize<Directive>(serializer.serialize(directive))).isNotNull().isDataClassEqualTo(directive)
    }

}