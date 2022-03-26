package io.qalipsis.core.directives

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class MinionsApiDirectivesTest {

    private val json = JsonSerializationModuleConfiguration().json()

    @Test
    fun `should encode and decode MinionsStartDirective as directive`() {
        val directive: Directive = MinionsStartDirective(
            "my-campaign",
            "scenario-id",
            listOf(MinionStartDefinition("1", 1))
        )
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode MinionsAssignmentDirective as directive`() {
        val directive: Directive = MinionsAssignmentDirective("campaign", "scenario-id")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode ScenarioWarmUpDirective as directive`() {
        val directive: Directive = ScenarioWarmUpDirective("campaign-id", "scenario-id", channel = "broadcast")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode MinionsShutdownDirective as directive`() {
        val directive: Directive =
            MinionsShutdownDirective("campaign-id", "scenario-id", listOf("minion-1", "minion-2"), "the-channel")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

}