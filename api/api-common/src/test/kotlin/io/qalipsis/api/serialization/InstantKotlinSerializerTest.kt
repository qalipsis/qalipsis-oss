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
import java.time.Instant

@Serializable
data class InstantTest(
    @Serializable(with = InstantKotlinSerializer::class) val instant: Instant
)

@ExperimentalSerializationApi
internal class InstantKotlinSerializerTest {

    @Test
    fun `should serialize and deserialize using kotlin serialization`() {
        val instantTest = InstantTest(Instant.now())
        val serialized = Json.encodeToString(instantTest)
        val result = Json.decodeFromString<InstantTest>(serialized)

        assertThat(result.instant.toEpochMilli()).isEqualTo(instantTest.instant.toEpochMilli())
    }
}