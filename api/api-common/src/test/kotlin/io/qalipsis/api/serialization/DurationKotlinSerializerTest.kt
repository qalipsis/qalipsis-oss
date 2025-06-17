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

package io.qalipsis.api.serialization

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Duration

@Serializable
data class DurationTest(
    @Serializable(with = DurationKotlinSerializer::class) val duration: Duration
)

@ExperimentalSerializationApi
internal class DurationKotlinSerializerTest {

    @Test
    fun `should serialize and deserialize using kotlin serialization`() {
        val durationTest = DurationTest(Duration.ofDays(2))
        val serialized = Json.encodeToString(durationTest)
        val result = Json.decodeFromString<DurationTest>(serialized)

        assertThat(result).isEqualTo(durationTest)
    }
}