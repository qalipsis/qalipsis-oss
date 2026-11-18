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
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

internal class NullRecordSerializerTest {

    private val serializer = NullRecordSerializer()

    @Test
    internal fun `should serialize and deserialize the record with null`() {
        assertThat(serializer.acceptsToSerialize(null)).isTrue()

        // when
        val result = serializer.serialize(null)

        // then
        assertThat(serializer.acceptsToDeserialize(result)).isTrue()

        // when
        val deserialized = serializer.deserialize<String>(result)

        // then
        assertThat(deserialized).isNull()
    }

    @Test
    internal fun `should not accept to deserialize record serialized with another one`() {
        val record = BinarySerializedRecord.from(ByteArray(0), Unit::class, "another")
        assertThat(serializer.acceptsToDeserialize(record)).isFalse()
    }
}