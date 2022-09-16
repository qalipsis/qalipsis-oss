package io.qalipsis.core.directives

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.qalipsis.core.configuration.ProtobufSerializationModuleConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class MinionsApiDirectivesTest {

    private val protobuf = ProtobufSerializationModuleConfiguration().protobuf()

    @Test
    fun `should encode and decode MinionsStartDirective as directive`() {
        val directive: Directive = MinionsStartDirective(
            "my-campaign",
            "scenario-id",
            listOf(MinionStartDefinition("1", 1))
        )
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode MinionsAssignmentDirective as directive`() {
        val directive: Directive = MinionsAssignmentDirective("campaign", "scenario-id")
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode ScenarioWarmUpDirective as directive`() {
        val directive: Directive = ScenarioWarmUpDirective("campaign-id", "scenario-id", channel = "broadcast")
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode MinionsShutdownDirective as directive`() {
        val directive: Directive =
            MinionsShutdownDirective("campaign-id", "scenario-id", listOf("minion-1", "minion-2"), "the-channel")
        val serialized = protobuf.encodeToByteArray(directive)
        val directiveFromSerialization = protobuf.decodeFromByteArray<Directive>(serialized)

        assertThat(directiveFromSerialization).isDataClassEqualTo(directive)
    }

}