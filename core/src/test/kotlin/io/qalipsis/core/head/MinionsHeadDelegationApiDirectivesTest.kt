package io.qalipsis.core.directives

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.qalipsis.api.orchestration.directives.DescriptiveDirective
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveReference
import io.qalipsis.api.orchestration.directives.SingleUseDirective
import io.qalipsis.api.orchestration.directives.SingleUseDirectiveReference
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import io.qalipsis.test.lang.TestIdGenerator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

internal class MinionsHeadDelegationApiDirectivesTest {

    private val json = JsonSerializationModuleConfiguration().json()

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirective reference directive as base class`() {
        val directive: SingleUseDirective<Int, SingleUseDirectiveReference<Int>> = MinionsCreationPreparationDirective("campaign", "scenario",1, channel = "broadcast", key = TestIdGenerator.short())

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<SingleUseDirective<Int, SingleUseDirectiveReference<Int>>>(jsonString)
        assertThat(convertedDirective).all {
            prop(SingleUseDirective<Int, SingleUseDirectiveReference<Int>>::key).isEqualTo(directive.key)
        }
    }


    @Test
    fun `should be able to serialize MinionsCreationPreparationDirective reference directive as directive`() {
        val directive: Directive = MinionsCreationPreparationDirective("campaign", "scenario",1, channel = "broadcast", key = TestIdGenerator.short())

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<Directive>(jsonString)
        assertThat(convertedDirective).all {
            prop(Directive::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirective reference directive implementation`() {
        val directive = MinionsCreationPreparationDirective("campaign", "scenario",1, channel = "broadcast", key = TestIdGenerator.short())

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<MinionsCreationPreparationDirective>(jsonString)
        assertThat(convertedDirective).all {
            prop(MinionsCreationPreparationDirective::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference reference directive as base class`() {
        val directive: SingleUseDirectiveReference<Int> = MinionsCreationPreparationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<SingleUseDirectiveReference<Int>>(jsonString)
        assertThat(convertedDirective).all {
            prop(SingleUseDirectiveReference<Int>::key).isEqualTo(directive.key)
        }
    }


    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference as directive`() {
        val directive: Directive = MinionsCreationPreparationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<Directive>(jsonString)
        assertThat(convertedDirective).all {
            prop(Directive::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference as directive reference`() {
        val directive: DirectiveReference = MinionsCreationPreparationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<DirectiveReference>(jsonString)
        assertThat(convertedDirective).all {
            prop(DirectiveReference::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference reference directive implementation`() {
        val directive = MinionsCreationPreparationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<MinionsCreationPreparationDirectiveReference>(jsonString)
        assertThat(convertedDirective).all {
            prop(MinionsCreationPreparationDirectiveReference::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirective as descriptive directive`() {
        val directive: DescriptiveDirective = MinionsRampUpPreparationDirective("campaign", "scenario", channel = "broadcast", key = TestIdGenerator.short())

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<DescriptiveDirective>(jsonString)
        assertThat(convertedDirective).all {
            prop(DescriptiveDirective::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirective as directive`() {
        val directive: Directive = MinionsRampUpPreparationDirective("campaign", "scenario", channel = "broadcast", key = TestIdGenerator.short())

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<Directive>(jsonString)
        assertThat(convertedDirective).all {
            prop(Directive::key).isEqualTo(directive.key)
        }
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirective reference directive implementation`() {
        val directive = MinionsRampUpPreparationDirective("campaign", "scenario", channel = "broadcast", key = TestIdGenerator.short())

        val jsonString = json.encodeToString(directive)

        val convertedDirective = json.decodeFromString<MinionsRampUpPreparationDirective>(jsonString)
        assertThat(convertedDirective).all {
            prop(MinionsRampUpPreparationDirective::key).isEqualTo(directive.key)
        }
    }
}