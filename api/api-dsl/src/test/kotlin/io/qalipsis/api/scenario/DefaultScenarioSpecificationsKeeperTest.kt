package io.qalipsis.api.scenario

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isNotNull
import assertk.assertions.key
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.scenario.catadioptre.filterScenarios
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 *
 * @author Eric Jessé
 */
internal class DefaultScenarioSpecificationsKeeperTest {

    @Test
    internal fun `should return all the scenarios when no selector is set`() {
        // given
        val keeper = DefaultScenarioSpecificationsKeeper(Optional.empty())

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { }
        )) as Map<*, *>

        // then
        assertThat(eligibleScenarios).hasSize(3)
    }

    @Test
    internal fun `should return all the scenarios when selector is blank`() {
        // given
        val keeper = DefaultScenarioSpecificationsKeeper(Optional.of("   "))

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { }
        )) as Map<*, *>

        // then
        assertThat(eligibleScenarios).hasSize(3)
    }

    @Test
    internal fun `should return all the scenarios when selector is a list of blanks`() {
        // given
        val keeper = DefaultScenarioSpecificationsKeeper(Optional.of("  ,  "))

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { }
        )) as Map<*, *>

        // then
        assertThat(eligibleScenarios).hasSize(3)
    }

    @Test
    internal fun `should return only the selected scenarios`() {
        // given
        val keeper =
            DefaultScenarioSpecificationsKeeper(Optional.of("my-second-scenario,my-f*th-scenario,my-fi???-scenario"))

        // when
        val eligibleScenarios = keeper.filterScenarios(mapOf(
            "my-first-scenario" to relaxedMockk { },
            "my-second-scenario" to relaxedMockk { },
            "my-third-scenario" to relaxedMockk { },
            "my-fourth-scenario" to relaxedMockk { },
            "my-fifth-scenario" to relaxedMockk { },
            "my-sixth-scenario" to relaxedMockk { }
        )) as Map<ScenarioId, *>

        // then
        assertThat(eligibleScenarios).all {
            hasSize(4)
            key("my-second-scenario").isNotNull()
            key("my-fourth-scenario").isNotNull()
            key("my-fifth-scenario").isNotNull()
            key("my-first-scenario").isNotNull()
        }
    }
}
