/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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