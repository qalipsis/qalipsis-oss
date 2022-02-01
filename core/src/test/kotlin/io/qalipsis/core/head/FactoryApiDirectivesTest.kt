package io.qalipsis.core.directives

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import io.qalipsis.test.lang.TestIdGenerator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class FactoryApiDirectivesTest {

    private val json = JsonSerializationModuleConfiguration().json()

    @Test
    internal fun `should encode and decode MinionsStartDirective as base class`() {
        val directive: DescriptiveDirective = MinionsStartDirective(
            "my-campaign",
            "scenario-id",
            listOf(MinionStartDefinition("1", 1)),
            channel = "broadcast",
            key = TestIdGenerator.short()
        )
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<DescriptiveDirective>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    internal fun `should encode and decode MinionsStartDirective as directive`() {
        val directive: Directive = MinionsStartDirective(
            "my-campaign",
            "scenario-id",
            listOf(MinionStartDefinition("1", 1)),
            channel = "broadcast",
            key = TestIdGenerator.short()
        )
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    internal fun `should encode and decode MinionsStartDirective using kotlin json serialization`() {
        val directive = MinionsStartDirective(
            "my-campaign",
            "scenario-id",
            listOf(MinionStartDefinition("1", 1)),
            channel = "broadcast",
            key = TestIdGenerator.short()
        )
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<MinionsStartDirective>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    internal fun `should encode and decode MinionsAssignmentDirective as base class`() {
        val directive: DescriptiveDirective =
            MinionsAssignmentDirective("campaign", "scenario-id", TestIdGenerator.short(), "broadcast")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<DescriptiveDirective>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    internal fun `should encode and decode MinionsAssignmentDirective as directive`() {
        val directive: Directive =
            MinionsAssignmentDirective("campaign", "scenario-id", TestIdGenerator.short(), "broadcast")
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    internal fun `should encode and decode MinionsAssignmentDirective using kotlin json serialization`() {
        val directive = MinionsAssignmentDirective(
            "campaign",
            "scenario-id",
            channel = "broadcast",
            key = TestIdGenerator.short()
        )
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<MinionsAssignmentDirective>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    internal fun `should encode and decode ScenarioWarmUpDirective as directive`() {
        val directive: Directive =
            ScenarioWarmUpDirective("campaign-id", "scenario-id", channel = "broadcast", key = TestIdGenerator.short())
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

    @Test
    internal fun `should encode and decode ScenarioWarmUpDirective as descriptive directive`() {
        val directive: DescriptiveDirective =
            ScenarioWarmUpDirective("campaign-id", "scenario-id", channel = "broadcast", key = TestIdGenerator.short())
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<DescriptiveDirective>(jsonString)

        assertThat(directiveFromJson).isDataClassEqualTo(directive)
    }

}