package io.qalipsis.core.directives

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.orchestration.directives.DescriptiveDirective
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveReference
import io.qalipsis.api.orchestration.directives.ListDirective
import io.qalipsis.api.orchestration.directives.ListDirectiveReference
import io.qalipsis.api.orchestration.directives.QueueDirective
import io.qalipsis.api.orchestration.directives.QueueDirectiveReference
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import io.qalipsis.test.lang.TestIdGenerator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

internal class MinionsApiDirectivesTest{

    @Test
    internal fun `should encode and decode MinionsStartDirectiveReference as base class`(){
        val directive: ListDirectiveReference<MinionStartDefinition> = MinionsStartDirectiveReference("my-directive", "scenario", channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson: ListDirectiveReference<MinionStartDefinition> = json.decodeFromString(jsonString)

        val castToSubType = directiveFromJson as MinionsStartDirectiveReference
        assertThat(castToSubType).all {
            prop(MinionsStartDirectiveReference::key).isEqualTo(directive.key)
            prop(MinionsStartDirectiveReference::scenarioId).isEqualTo("scenario")
        }
    }

    @Test
    internal fun `should encode and decode MinionsStartDirectiveReference as directive`(){
        val directive: Directive = MinionsStartDirectiveReference("my-directive", "scenario", channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson: Directive = json.decodeFromString(jsonString)

        val castToSubType = directiveFromJson as MinionsStartDirectiveReference
        assertThat(castToSubType).all {
            prop(MinionsStartDirectiveReference::key).isEqualTo(directive.key)
            prop(MinionsStartDirectiveReference::scenarioId).isEqualTo("scenario")
        }
    }

    @Test
    internal fun `should encode and decode MinionsStartDirectiveReference as DirectiveReference`(){
        val directive: DirectiveReference = MinionsStartDirectiveReference("my-directive", "scenario", channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson: DirectiveReference = json.decodeFromString(jsonString)

        val castToSubType = directiveFromJson as MinionsStartDirectiveReference
        assertThat(castToSubType).all {
            prop(MinionsStartDirectiveReference::key).isEqualTo(directive.key)
            prop(MinionsStartDirectiveReference::scenarioId).isEqualTo("scenario")
        }
    }

    @Test
    internal fun `should encode and decode MinionsStartDirectiveReference using kotlin json serialization`(){
        val directive = MinionsStartDirectiveReference("my-directive", "scenario", channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<MinionsStartDirectiveReference>(jsonString)

        assertThat(directiveFromJson).all {
            prop(MinionsStartDirectiveReference::scenarioId).isEqualTo(directive.scenarioId)
            prop(MinionsStartDirectiveReference::key).isEqualTo(directive.key)
        }
    }

    @Test
    internal fun `should encode and decode MinionsStartDirective as base class`(){
        val directive: ListDirective<MinionStartDefinition, ListDirectiveReference<MinionStartDefinition>> = MinionsStartDirective("scenario-id", listOf(MinionStartDefinition("1", 1)), channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<ListDirective<MinionStartDefinition, ListDirectiveReference<MinionStartDefinition>>>(jsonString)

        val castToSubType = directiveFromJson as MinionsStartDirective
        assertThat(castToSubType).all {
            prop(MinionsStartDirective::key).isEqualTo(directive.key)
            prop(MinionsStartDirective::scenarioId).isEqualTo("scenario-id")
            prop(MinionsStartDirective::values).isEqualTo(listOf(MinionStartDefinition("1", 1)))
        }
    }

    @Test
    internal fun `should encode and decode MinionsStartDirective as directive`(){
        val directive: Directive = MinionsStartDirective("scenario-id", listOf(MinionStartDefinition("1", 1)), channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        val castToSubType = directiveFromJson as MinionsStartDirective
        assertThat(castToSubType).all {
            prop(MinionsStartDirective::key).isEqualTo(directive.key)
            prop(MinionsStartDirective::scenarioId).isEqualTo("scenario-id")
            prop(MinionsStartDirective::values).isEqualTo(listOf(MinionStartDefinition("1", 1)))
        }
    }

    @Test
    internal fun `should encode and decode MinionsStartDirective using kotlin json serialization`(){
        val directive = MinionsStartDirective("scenario-id", listOf(MinionStartDefinition("1", 1)), channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<MinionsStartDirective>(jsonString)

        assertThat(directiveFromJson).all {
            prop(MinionsStartDirective::scenarioId).isEqualTo(directive.scenarioId)
            prop(MinionsStartDirective::key).isEqualTo(directive.key)
        }
    }

    @Test
    internal fun `should encode and decode MinionsCreationDirective as base class`(){
        val directive: QueueDirective<MinionId, QueueDirectiveReference<MinionId>> = MinionsCreationDirective("campaign", "scenario-id", DirectedAcyclicGraphId(), listOf(MinionId()), channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<QueueDirective<MinionId, QueueDirectiveReference<MinionId>>>(jsonString)

        val castToSubType = directiveFromJson as MinionsCreationDirective
        assertThat(castToSubType).all {
            prop(MinionsCreationDirective::key).isEqualTo(directive.key)
            prop(MinionsCreationDirective::scenarioId).isEqualTo("scenario-id")
            prop(MinionsCreationDirective::values).isEqualTo(listOf(MinionId()))
        }
    }

    @Test
    internal fun `should encode and decode MinionsCreationDirective as directive`(){
        val directive: Directive = MinionsCreationDirective("campaign", "scenario-id", DirectedAcyclicGraphId(), listOf(MinionId()), channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        val castToSubType = directiveFromJson as MinionsCreationDirective
        assertThat(castToSubType).all {
            prop(MinionsCreationDirective::key).isEqualTo(directive.key)
            prop(MinionsCreationDirective::scenarioId).isEqualTo("scenario-id")
            prop(MinionsCreationDirective::values).isEqualTo(listOf(MinionId()))
        }
    }

    @Test
    internal fun `should encode and decode MinionsCreationDirective using kotlin json serialization`(){
        val directive = MinionsCreationDirective("campaign", "scenario-id", DirectedAcyclicGraphId(), listOf(MinionId()), channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<MinionsCreationDirective>(jsonString)

        assertThat(directiveFromJson).all {
            prop(MinionsCreationDirective::scenarioId).isEqualTo(directive.scenarioId)
            prop(MinionsCreationDirective::key).isEqualTo(directive.key)
        }
    }

    @Test
    internal fun `should encode and decode MinionsCreationDirectiveReference as base class`(){
        val directive: QueueDirectiveReference<MinionId> = MinionsCreationDirectiveReference("1", "campaign", "scenario-id", DirectedAcyclicGraphId(), channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<QueueDirectiveReference<MinionId>>(jsonString)

        val castToSubType = directiveFromJson as MinionsCreationDirectiveReference
        assertThat(castToSubType).all {
            prop(MinionsCreationDirectiveReference::key).isEqualTo(directive.key)
            prop(MinionsCreationDirectiveReference::scenarioId).isEqualTo("scenario-id")
        }
    }

    @Test
    internal fun `should encode and decode MinionsCreationDirectiveReference as directive`(){
        val directive: Directive = MinionsCreationDirectiveReference("1", "campaign", "scenario-id", DirectedAcyclicGraphId(), channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        val castToSubType = directiveFromJson as MinionsCreationDirectiveReference
        assertThat(castToSubType).all {
            prop(MinionsCreationDirectiveReference::key).isEqualTo(directive.key)
            prop(MinionsCreationDirectiveReference::scenarioId).isEqualTo("scenario-id")
        }
    }

    @Test
    internal fun `should encode and decode MinionsCreationDirectiveReference as directive reference`(){
        val directive: DirectiveReference = MinionsCreationDirectiveReference("1", "campaign", "scenario-id", DirectedAcyclicGraphId(), channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<DirectiveReference>(jsonString)

        val castToSubType = directiveFromJson as MinionsCreationDirectiveReference
        assertThat(castToSubType).all {
            prop(MinionsCreationDirectiveReference::key).isEqualTo(directive.key)
            prop(MinionsCreationDirectiveReference::scenarioId).isEqualTo("scenario-id")
        }
    }

    @Test
    internal fun `should encode and decode MinionsCreationDirectiveReference using kotlin json serialization`(){
        val directive = MinionsCreationDirectiveReference("1", "campaign", "scenario-id", DirectedAcyclicGraphId(), channel = "broadcast")
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<MinionsCreationDirectiveReference>(jsonString)

        assertThat(directiveFromJson).all {
            prop(MinionsCreationDirectiveReference::scenarioId).isEqualTo(directive.scenarioId)
            prop(MinionsCreationDirectiveReference::key).isEqualTo(directive.key)
        }
    }

    @Test
    internal fun `should encode and decode CampaignStartDirective as directive`(){
        val directive: Directive = CampaignStartDirective("campaign-id", "scenario-id", channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<Directive>(jsonString)

        val castToSubType = directiveFromJson as CampaignStartDirective
        assertThat(castToSubType).all {
            prop(CampaignStartDirective::key).isEqualTo(directive.key)
            prop(CampaignStartDirective::scenarioId).isEqualTo("scenario-id")
        }
    }

    @Test
    internal fun `should encode and decode CampaignStartDirective as descriptive directive`(){
        val directive: DescriptiveDirective = CampaignStartDirective("campaign-id", "scenario-id", channel = "broadcast", key = TestIdGenerator.short())
        val json = JsonSerializationModuleConfiguration().json()
        val jsonString = json.encodeToString(directive)
        val directiveFromJson = json.decodeFromString<DescriptiveDirective>(jsonString)

        val castToSubType = directiveFromJson as CampaignStartDirective
        assertThat(castToSubType).all {
            prop(CampaignStartDirective::key).isEqualTo(directive.key)
            prop(CampaignStartDirective::scenarioId).isEqualTo("scenario-id")
        }
    }

}