package io.qalipsis.core.directives

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.qalipsis.core.configuration.JsonSerializationModuleConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
internal class MinionsHeadDelegationApiDirectivesTest {

    private val json = JsonSerializationModuleConfiguration().json()

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirective reference directive as directive`() {
        val directive: Directive = MinionsDeclarationDirective("campaign", "scenario", 1, channel = "broadcast")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsCreationPreparationDirectiveReference as directive`() {
        val directive: Directive = MinionsDeclarationDirectiveReference("campaign", "scenario", "1")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirective as directive`() {
        val directive: Directive = MinionsRampUpPreparationDirective(
            "campaign",
            "scenario",
            channel = "broadcast"
        )
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

    @Test
    fun `should be able to serialize MinionsRampUpPreparationDirectiveReference as directive`() {
        val directive: Directive = MinionsRampUpPreparationDirectiveReference("any", "campaign", "scenario")
        val jsonString = json.encodeToString(directive)
        val convertedDirective = json.decodeFromString<Directive>(jsonString)

        assertThat(convertedDirective).isDataClassEqualTo(directive)
    }

}