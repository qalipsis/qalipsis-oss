/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.serialization

import io.qalipsis.core.directives.MinionsRampUpPreparationDirective
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
internal class BinarySerializedRecordSerializerTest {

    private val protobuf = SerializationFactory().protobuf()

    @Test
    internal fun `should serialize a record with a non empty value`() {
        // given
        val record = BinarySerializedRecord(
            "This is a test".encodeToByteArray(),
            typeName = String::class.java.name,
            serializer = "string"
        )

        // when
        val serialized = protobuf.encodeToByteArray(record)
        val deserialized = protobuf.decodeFromByteArray<BinarySerializedRecord>(serialized)

        // then
        Assertions.assertEquals(record, deserialized)
    }

    @Test
    internal fun `should serialize a record with an empty value`() {
        // given
        val record =
            BinarySerializedRecord(ByteArray(0), typeName = String::class.java.name, serializer = "string")

        // when
        val serialized = protobuf.encodeToByteArray(record)
        val deserialized = protobuf.decodeFromByteArray<BinarySerializedRecord>(serialized)

        // then
        Assertions.assertEquals(record, deserialized)
    }

    @Test
    internal fun `should serialize a directive`() {
        // given
        val directive = MinionsRampUpPreparationDirective(
            campaignKey = "cl892t2d90002g9569i7n2x6i",
            scenarioName = "deployment-test",
            executionProfileConfiguration = DefaultExecutionProfileConfiguration(),
            channel = "directives-broadcast"
        )
        val record = BinarySerializedRecord(
            protobuf.encodeToByteArray(directive),
            typeName = MinionsRampUpPreparationDirective::class.java.name,
            serializer = "string"
        )

        // when
        val serialized = protobuf.encodeToByteArray(record)
        val deserialized = protobuf.decodeFromByteArray<BinarySerializedRecord>(serialized)

        // then
        Assertions.assertEquals(record, deserialized)
        val deserializedDirective = protobuf.decodeFromByteArray<MinionsRampUpPreparationDirective>(deserialized.value)
        Assertions.assertEquals(directive, deserializedDirective)
    }

}