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

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import io.qalipsis.api.serialization.JsonSerializers
import io.qalipsis.api.serialization.SerializersProvider
import io.qalipsis.untrusted.TRAPPED_TYPE_NAME
import io.qalipsis.untrusted.TrapState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalSerializationApi::class)
internal class RecordDistributionSerializerTest {

    private val protoBuf = SerializationFactory().protobuf()

    private val serializersProvider = SerializersProvider(listOf(JsonSerializers.json, protoBuf))

    private val serializer = RecordDistributionSerializer(
        listOf(
            NullRecordSerializer(),
            SerialFormatRecordSerializer(serializersProvider),
            JacksonJsonRecordSerializer(
                DefaultRecordTypeResolver(serializersProvider, listOf(ALLOWED_TEST_PACKAGE))
            )
        ),
        protoBuf
    )

    @Test
    fun `given allowed values when round-tripping then the values are restored`() {
        listOf("a text", 12, 3.5, arrayListOf("a", "b"), linkedMapOf("key" to "value")).forEach { value ->
            assertThat(serializer.deserialize<Any>(serializer.serialize(value))).isEqualTo(value)
        }
        assertThat(serializer.deserialize<String?>(serializer.serialize(null))).isEqualTo(null)
    }

    @Test
    fun `given a value of an allowed package when round-tripping then the value is restored`() {
        val value = AllowedRecord("alice", 38)

        assertThat(serializer.deserialize<AllowedRecord>(serializer.serialize(value))).isNotNull()
            .isDataClassEqualTo(value)
    }

    @Test
    fun `given a JSON record naming a not allowed type when deserializing then it is rejected and the type not initialized`() {
        val bytes = protoBuf.encodeToByteArray<SerializedRecord>(
            BinarySerializedRecord("""{"value":"x"}""".encodeToByteArray(), TRAPPED_TYPE_NAME, "jackson-json")
        )

        assertThrows<RejectedRecordTypeException> { serializer.deserialize<Any>(bytes) }

        assertThat(TrapState.initialized.get()).isFalse()
    }

    @Test
    fun `given a record of a known serial format naming a type without serializer when deserializing then it is rejected`() {
        val bytes = protoBuf.encodeToByteArray<SerializedRecord>(
            BinarySerializedRecord(ByteArray(0), TRAPPED_TYPE_NAME, "kprotobuf")
        )

        assertThrows<RejectedRecordTypeException> { serializer.deserialize<Any>(bytes) }

        assertThat(TrapState.initialized.get()).isFalse()
    }

    data class AllowedRecord(val name: String, val age: Int)

    private companion object {
        const val ALLOWED_TEST_PACKAGE = "io.qalipsis.core.serialization"
    }
}
