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
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.spyk
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.test.assertk.prop
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class PercentageStageExecutionProfileTest {

    @Test
    internal fun `should define the strategy on the scenario`() {
        val scenario = TestScenarioFactory.scenario {
            profile {
                stages {
                    stage(
                        minionsPercentage = 25.0,
                        rampUpDuration = Duration.ofSeconds(12),
                        totalDuration = Duration.ofSeconds(12),
                        resolution = Duration.ofMillis(500)
                    )
                    stage(minionsPercentage = 75.0, rampUpDurationMs = 500, totalDurationMs = 1500)
                }
            }
        }

        assertThat(scenario).prop("executionProfile").isEqualTo(
            PercentageStageExecutionProfile(
                CompletionMode.GRACEFUL, listOf(
                    PercentageStage(
                        minionsPercentage = 25.0,
                        rampUpDurationMs = 12000,
                        totalDurationMs = 12000,
                        resolutionMs = 500
                    ),
                    PercentageStage(
                        minionsPercentage = 75.0,
                        rampUpDurationMs = 500,
                        totalDurationMs = 1500,
                        resolutionMs = 500
                    )
                )
            )
        )
    }

    @Test
    internal fun `should start a unique minion when the total is 1`() {
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 25.0,
                rampUpDurationMs = 12000,
                totalDurationMs = 12000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 75.0,
                rampUpDurationMs = 500,
                totalDurationMs = 1500,
                resolutionMs = 500
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.GRACEFUL, stages)

        val iterator = executionProfile.iterator(1, 1.0)

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            lines.add(iterator.next())
        }

        assertThat(lines).all {
            hasSize(1)
            containsExactly(
                MinionsStartingLine(1, 0)
            )
        }
    }

    @Test
    internal fun `should fail when the total of percentage is not 100`() {
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 25.0,
                rampUpDurationMs = 12000,
                totalDurationMs = 12000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 35.0,
                rampUpDurationMs = 500,
                totalDurationMs = 1500,
                resolutionMs = 500
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.GRACEFUL, stages)

        val exception = assertThrows<IllegalArgumentException> { executionProfile.iterator(1, 1.0) }

        assertThat(exception.message).isEqualTo("The sum of the percentages of all execution profile stages should be 100% but was 60.0%")
    }

    @Test
    internal fun `should provide constant count within each stage as long as there are minions to start`() {
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.HARD, stages)

        val iterator = spyk(executionProfile.iterator(32, 1.0))

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            lines.add(iterator.next())
        }

        assertThat(lines).all {
            hasSize(7)
            containsExactly(
                MinionsStartingLine(3, 0),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(3, 500),
                MinionsStartingLine(4, 500),
                MinionsStartingLine(7, 1000),
                MinionsStartingLine(7, 400),
                MinionsStartingLine(5, 400),
            )
        }
    }

    @Test
    internal fun `should provide constant count within each stage applying the factor`() {
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.HARD, stages)

        val iterator = spyk(executionProfile.iterator(39, 2.0))

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            lines.add(iterator.next())
        }

        assertThat(lines).all {
            hasSize(7)
            containsExactly(
                MinionsStartingLine(4, 0),
                MinionsStartingLine(4, 250),
                MinionsStartingLine(4, 250),
                MinionsStartingLine(4, 250),
                MinionsStartingLine(8, 500),
                MinionsStartingLine(8, 200),
                MinionsStartingLine(7, 200),
            )
        }
    }

    @Test
    internal fun `should replay when the completion is HARD and the remaining time is more than the elapsed one`() {
        // given
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.HARD, stages)
        executionProfile.notifyStart(1.0)

        // when
        val canReplay = executionProfile.canReplay(Duration.ofMillis(4600))

        // then
        assertThat(canReplay).isTrue()
    }

    @Test
    internal fun `should not replay when the completion is HARD and the remaining time is less than the elapsed one`() {
        // given
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.HARD, stages)
        executionProfile.notifyStart(1.0)

        // when
        val canReplay = executionProfile.canReplay(Duration.ofMillis(5300))

        // then
        assertThat(canReplay).isFalse()
    }

    @Test
    internal fun `should not replay when the completion is HARD and the speed factor greater than 1 and the remaining time is less than the elapsed one`() {
        // given
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 1500,
                totalDurationMs = 2000,
                resolutionMs = 400
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.HARD, stages)
        executionProfile.notifyStart(1.5)

        // when
        val canReplay = executionProfile.canReplay(Duration.ofMillis(4600))

        // then
        assertThat(canReplay).isFalse()
    }

    @Test
    internal fun `should replay when the completion is GRACEFUL and the end of the stages is not reached`() {
        // given
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 2000,
                totalDurationMs = 3000,
                resolutionMs = 500
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 300,
                totalDurationMs = 300,
                resolutionMs = 40
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.GRACEFUL, stages)
        executionProfile.notifyStart(1.0)

        // when
        Thread.sleep(400)
        val canReplay = executionProfile.canReplay(Duration.ofMinutes(10_000))

        // then
        assertThat(canReplay).isTrue()
    }

    @Test
    internal fun `should not replay when the completion is GRACEFUL and the end of the stages is reached`() {
        // given
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 100,
                totalDurationMs = 200,
                resolutionMs = 50
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 300,
                totalDurationMs = 300,
                resolutionMs = 40
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.GRACEFUL, stages)
        executionProfile.notifyStart(1.0)

        // when
        Thread.sleep(600)
        val canReplay = executionProfile.canReplay(Duration.ofMinutes(10_000))

        // then
        assertThat(canReplay).isFalse()
    }

    @Test
    internal fun `should not replay when the completion is GRACEFUL and the speed factor greater than 1 and the end of the stages is not reached`() {
        // given
        val stages = listOf(
            PercentageStage(
                minionsPercentage = 40.0,
                rampUpDurationMs = 100,
                totalDurationMs = 200,
                resolutionMs = 50
            ),
            PercentageStage(
                minionsPercentage = 60.0,
                rampUpDurationMs = 300,
                totalDurationMs = 300,
                resolutionMs = 40
            ),
        )
        val executionProfile = PercentageStageExecutionProfile(CompletionMode.GRACEFUL, stages)
        executionProfile.notifyStart(2.0)

        // when
        Thread.sleep(300)
        val canReplay = executionProfile.canReplay(minionExecutionDuration = Duration.ofMinutes(10_000))

        // then
        assertThat(canReplay).isFalse()
    }
}