/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
