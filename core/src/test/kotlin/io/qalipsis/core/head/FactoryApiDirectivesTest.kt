package io.qalipsis.core.head

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import io.qalipsis.core.directives.CampaignScenarioShutdownDirective
import io.qalipsis.core.directives.CampaignShutdownDirective
import io.qalipsis.core.directives.CompleteCampaignDirective
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryAssignmentDirective
import io.qalipsis.core.directives.ScenarioWarmUpDirective
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class FactoryApiDirectivesTest {

    private val json = JsonSerializationModuleConfiguration().json()

    @Test
    fun `should encode and decode FactoryAssignmentDirective as directive`() {
        val directive: Directive = FactoryAssignmentDirective(
            "my-campaign",
            mapOf("my-scenario-1" to listOf("dag-1", "dag-2"), "my-scenario-2" to listOf("dag-3", "dag-4")),
            broadcastChannel = "broadcast-channel",
            feedbackChannel = "feedback-channel",
            channel = "broadcast"
        )
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode ScenarioWarmUpDirective as directive`() {
        val directive: Directive = ScenarioWarmUpDirective("my-campaign", "my-scenario-1", "the-channel")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode CampaignScenarioShutdownDirective as directive`() {
        val directive: Directive = CampaignScenarioShutdownDirective("my-campaign", "my-scenario-1", "the-channel")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode CampaignShutdownDirective as directive`() {
        val directive: Directive = CampaignShutdownDirective("my-campaign", "the-channel")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    fun `should encode and decode CompleteCampaignDirective as directive`() {
        val directive: Directive =
            CompleteCampaignDirective("my-campaign", true, "the completion message", "the-channel")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }
}