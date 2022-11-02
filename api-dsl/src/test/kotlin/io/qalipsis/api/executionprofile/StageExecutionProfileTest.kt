/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.executionprofile

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.spyk
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.verifyExactly
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Svetlana Paliashchuk
 */
internal class StageExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                stages {
                    stage(
                        minionsCount = 234,
                        rampUpDuration = Duration.ofSeconds(12),
                        totalDuration = Duration.ofSeconds(12),
                        resolution = Duration.ofMillis(500)
                    )
                    stage(minionsCount = 15, rampUpDurationMs = 500, totalDurationMs = 1500, resolutionMs = 500)
                }
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(
            StageExecutionProfile(
                listOf(
                    Stage(minionsCount = 234, rampUpDurationMs = 12000, totalDurationMs = 12000, resolutionMs = 500),
                    Stage(minionsCount = 15, rampUpDurationMs = 500, totalDurationMs = 1500, resolutionMs = 500)
                ), CompletionMode.FORCED
            )
        )
    }

    @Test
    internal fun `should provide constant count for each stage`() {
        val stages = listOf(
            Stage(
                minionsCount = 12,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            Stage(
                minionsCount = 14,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = StageExecutionProfile(stages, CompletionMode.FORCED)

        val iterator = spyk(executionProfile.iterator(31, 1.0))

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            val next = iterator.next()
            lines.add(next)
        }

        verifyExactly(7) {
            iterator.next()
        }

        assertThat(lines).all {
            hasSize(7)
            containsExactly(
                MinionsStartingLine(3, 0),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(4, 1000),
                MinionsStartingLine(4, 400),
                MinionsStartingLine(4, 400),
            )
        }
    }

    @Test
    internal fun `should provide constant count for each stage with factor`() {
        val stages = listOf(
            Stage(
                minionsCount = 12,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            Stage(
                minionsCount = 14,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = StageExecutionProfile(stages, CompletionMode.FORCED)

        val iterator = spyk(executionProfile.iterator(31, 2.0))

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            val next = iterator.next()
            lines.add(next)
        }

        verifyExactly(7) {
            iterator.next()
        }

        assertThat(lines).all {
            hasSize(7)
            containsExactly(
                MinionsStartingLine(3, 0),
                MinionsStartingLine(3, 250),
                MinionsStartingLine(3, 250),
                MinionsStartingLine(3, 250),
                MinionsStartingLine(4, 500),
                MinionsStartingLine(4, 200),
                MinionsStartingLine(4, 200),
            )
        }
    }

    @Test
    internal fun `should provide constant count for each stage when total minions count is less than minions count in all stages`() {
        val stages = listOf(
            Stage(
                minionsCount = 12,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            Stage(
                minionsCount = 14,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = StageExecutionProfile(stages, CompletionMode.FORCED)

        val iterator = spyk(executionProfile.iterator(19, 1.0))

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            lines.add(iterator.next())
        }

        verifyExactly(6) {
            iterator.next()
        }

        assertThat(lines).all {
            hasSize(6)
            transform { it.sumOf { it.count } }.isEqualTo(19)
            containsExactly(
                MinionsStartingLine(3, 0),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(4, 1000),
                MinionsStartingLine(3, 400)
            )
        }
    }
}