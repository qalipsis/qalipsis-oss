package io.qalipsis.api.serialization

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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