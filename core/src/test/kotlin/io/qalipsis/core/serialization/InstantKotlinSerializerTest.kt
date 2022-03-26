package io.qalipsis.core.serialization

import assertk.assertThat
import assertk.assertions.isEqualTo
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

internal class InstantKotlinSerializerTest {

    @Test
    fun `should serialize and deserialize using kotlin serialization`() {
        val instantTest = InstantTest(Instant.now())
        val serializable = Json.encodeToString(instantTest)
        val result = Json.decodeFromString<InstantTest>(serializable)

        assertThat(result.instant.toEpochMilli()).isEqualTo(instantTest.instant.toEpochMilli())
    }
}