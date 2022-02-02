package io.qalipsis.core.serialization

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Duration

@Serializable
data class DurationTest(
    @Serializable(with = DurationKotlinSerializer::class) val duration: Duration
)

internal class DurationKotlinSerializerTest{

    @Test
    fun `should serialize and deserialize using kotlin serialization`(){
        val durationTest = DurationTest(Duration.ofDays(2))
        val serializable = Json.encodeToString(durationTest)
        val result = Json.decodeFromString<DurationTest>(serializable)

        assertThat(result).isEqualTo(durationTest)
    }
}