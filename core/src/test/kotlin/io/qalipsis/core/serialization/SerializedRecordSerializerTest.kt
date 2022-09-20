/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

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

package io.qalipsis.core.serialization

import io.qalipsis.api.serialization.SerializableClass
import io.qalipsis.core.configuration.ProtobufSerializationModuleConfiguration
import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
internal class SerializedRecordSerializerTest {

    private val protobuf = ProtobufSerializationModuleConfiguration().protobuf()

    @Test
    internal fun `should serialize a record with a non empty value`() {
        // given
        val record =
            SerializedRecord("This is a test".encodeToByteArray(), SerializableClass(String::class.java), "string")

        // when
        val serialized = protobuf.encodeToByteArray(record)
        val deserialized = protobuf.decodeFromByteArray<SerializedRecord>(serialized)

        // then
        Assertions.assertEquals(record, deserialized)
    }

    @Test
    internal fun `should serialize a record with an empty value`() {
        // given
        val record = SerializedRecord(ByteArray(0), SerializableClass(String::class.java), "string")

        // when
        val serialized = protobuf.encodeToByteArray(record)
        val deserialized = protobuf.decodeFromByteArray<SerializedRecord>(serialized)

        // then
        Assertions.assertEquals(record, deserialized)
    }

    @Test
    internal fun `should serialize a directive`() {
        // given
        val directive = MinionsRampUpPreparationDirective(
            campaignKey = "cl892t2d90002g9569i7n2x6i",
            scenarioName = "deployment-test",
            executionProfileConfiguration = DefaultExecutionProfileConfiguration(
                startOffsetMs = 1000,
                speedFactor = 1.0
            ),
            channel = "directives-broadcast"
        )
        val record = SerializedRecord(
            protobuf.encodeToByteArray(directive),
            SerializableClass(MinionsRampUpPreparationDirective::class.java),
            "string"
        )

        // when
        val serialized = protobuf.encodeToByteArray(record)
        val deserialized = protobuf.decodeFromByteArray<SerializedRecord>(serialized)

        // then
        Assertions.assertEquals(record, deserialized)
        val deserializedDirective = protobuf.decodeFromByteArray<MinionsRampUpPreparationDirective>(deserialized.value)
        Assertions.assertEquals(directive, deserializedDirective)
    }


}