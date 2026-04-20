/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.processors.configuration

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

internal class ConfigurationReferenceAnnotationProcessorTest {

    @Test
    internal fun `should convert camelCase to kebab-case`() {
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("nodeId")).isEqualTo("node-id")
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("evaluationBatchSize")).isEqualTo("evaluation-batch-size")
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("ttl")).isEqualTo("ttl")
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("keyPrefix")).isEqualTo("key-prefix")
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("maxScenarioStepSpecificationsCount")).isEqualTo(
            "max-scenario-step-specifications-count"
        )
    }

    @Test
    internal fun `should convert single word to lowercase`() {
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("zone")).isEqualTo("zone")
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("tags")).isEqualTo("tags")
    }

    @Test
    internal fun `should handle already kebab-case names`() {
        assertThat(ConfigurationReferenceAnnotationProcessor.toKebabCase("node-id")).isEqualTo("node-id")
    }
}
