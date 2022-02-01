package io.qalipsis.core.directives

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import io.qalipsis.core.rampup.RampUpConfiguration
import io.qalipsis.test.lang.TestIdGenerator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class MinionsHeadDelegationApiDirectivesTest {

    private val json = JsonSerializationModuleConfiguration().json()

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirective reference directive as base class`() {
        val directive: SingleUseDirective<Int, SingleUseDirectiveReference<Int>> =
            MinionsDeclarationDirective("campaign", "scenario", 1, channel = "broadcast", key = TestIdGenerator.short())
        val jsonString = json.encodeToString(directive)
        val convertedDirective =
            json.decodeFromString<SingleUseDirective<Int, SingleUseDirectiveReference<Int>>>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }


    @Test
    fun `should be able to serialize MinionsCreationPreparationDirective reference directive as directive`() {
        val directive: Directive =
            MinionsDeclarationDirective("campaign", "scenario", 1, channel = "broadcast", key = TestIdGenerator.short())
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirective reference directive implementation`() {
        val directive =
            MinionsDeclarationDirective("campaign", "scenario", 1, channel = "broadcast", key = TestIdGenerator.short())
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<MinionsDeclarationDirective>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference reference directive as base class`() {
        val directive: SingleUseDirectiveReference<Int> =
            MinionsDeclarationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<SingleUseDirectiveReference<Int>>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }


    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference as directive`() {
        val directive: Directive =
            MinionsDeclarationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference as directive reference`() {
        val directive: DirectiveReference =
            MinionsDeclarationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<DirectiveReference>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference reference directive implementation`() {
        val directive = MinionsDeclarationDirectiveReference("campaign", "scenario", "1", channel = "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<MinionsDeclarationDirectiveReference>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirective as single use directive`() {
        val directive: SingleUseDirective<RampUpConfiguration, SingleUseDirectiveReference<RampUpConfiguration>> =
            MinionsRampUpPreparationDirective(
                "campaign",
                "scenario",
                channel = "broadcast",
                key = TestIdGenerator.short()
            )
        val jsonString = json.encodeToString(directive)
        val convertedDirective =
            json.decodeFromString<SingleUseDirective<RampUpConfiguration, SingleUseDirectiveReference<RampUpConfiguration>>>(
                jsonString
            )

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirective as directive`() {
        val directive: Directive = MinionsRampUpPreparationDirective(
            "campaign",
            "scenario",
            channel = "broadcast",
            key = TestIdGenerator.short()
        )
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirective reference directive implementation`() {
        val directive = MinionsRampUpPreparationDirective(
            "campaign",
            "scenario",
            channel = "broadcast",
            key = TestIdGenerator.short()
        )
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<MinionsRampUpPreparationDirective>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }


    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirectiveReference reference directive as base class`() {
        val directive: SingleUseDirectiveReference<RampUpConfiguration> =
            MinionsRampUpPreparationDirectiveReference("any", "campaign", "scenario", "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<SingleUseDirectiveReference<RampUpConfiguration>>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirectiveReference as directive`() {
        val directive: Directive =
            MinionsRampUpPreparationDirectiveReference("any", "campaign", "scenario", "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirectiveReference as directive reference`() {
        val directive: DirectiveReference =
            MinionsRampUpPreparationDirectiveReference("any", "campaign", "scenario", "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<DirectiveReference>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirectiveReference reference directive implementation`() {
        val directive = MinionsRampUpPreparationDirectiveReference("any", "campaign", "scenario", "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<MinionsRampUpPreparationDirectiveReference>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }
}