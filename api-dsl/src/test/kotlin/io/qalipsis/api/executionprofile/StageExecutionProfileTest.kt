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
                CompletionMode.GRACEFUL, listOf(
                    Stage(minionsCount = 234, rampUpDurationMs = 12000, totalDurationMs = 12000, resolutionMs = 500),
                    Stage(minionsCount = 15, rampUpDurationMs = 500, totalDurationMs = 1500, resolutionMs = 500)
                )
            )
        )
    }

    @Test
    internal fun `should start a unique minion`() {
        val stages = listOf(
            Stage(
                minionsCount = 1,
                rampUpDurationMs = 5000,
                totalDurationMs = 5000,
                resolutionMs = 500
            ),
        )
        val executionProfile = StageExecutionProfile(CompletionMode.GRACEFUL, stages)

        val iterator = executionProfile.iterator(1, 1.0)

        val lines = mutableListOf<MinionsStartingLine>()
        while (iterator.hasNext()) {
            val next = iterator.next()
            lines.add(next)
        }

        assertThat(lines).all {
            hasSize(1)
            containsExactly(
                MinionsStartingLine(1, 0)
            )
        }
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
        val executionProfile = StageExecutionProfile(CompletionMode.HARD, stages)

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
                MinionsStartingLine(5, 1000),
                MinionsStartingLine(5, 400),
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
        val executionProfile = StageExecutionProfile(CompletionMode.HARD, stages)

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
                MinionsStartingLine(5, 500),
                MinionsStartingLine(5, 200),
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
        val executionProfile = StageExecutionProfile(CompletionMode.HARD, stages)

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
                MinionsStartingLine(5, 1000),
                MinionsStartingLine(2, 400)
            )
        }
    }

    @Test
    internal fun `should replay when the completion is HARD and the remaining time is more than the elapsed one`() {
        // given
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
        val executionProfile = StageExecutionProfile(CompletionMode.HARD, stages)
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
        val executionProfile = StageExecutionProfile(CompletionMode.HARD, stages)
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
        val executionProfile = StageExecutionProfile(CompletionMode.HARD, stages)
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
            Stage(
                minionsCount = 12,
                rampUpDurationMs = 100,
                totalDurationMs = 200,
                resolutionMs = 50
            ),
            Stage(
                minionsCount = 14,
                rampUpDurationMs = 300,
                totalDurationMs = 300,
                resolutionMs = 40
            ),
        )
        val executionProfile = StageExecutionProfile(CompletionMode.GRACEFUL, stages)
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
            Stage(
                minionsCount = 12,
                rampUpDurationMs = 100,
                totalDurationMs = 200,
                resolutionMs = 50
            ),
            Stage(
                minionsCount = 14,
                rampUpDurationMs = 300,
                totalDurationMs = 300,
                resolutionMs = 40
            ),
        )
        val executionProfile = StageExecutionProfile(CompletionMode.GRACEFUL, stages)
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
            Stage(
                minionsCount = 12,
                rampUpDurationMs = 100,
                totalDurationMs = 200,
                resolutionMs = 50
            ),
            Stage(
                minionsCount = 14,
                rampUpDurationMs = 300,
                totalDurationMs = 300,
                resolutionMs = 40
            ),
        )
        val executionProfile = StageExecutionProfile(CompletionMode.GRACEFUL, stages)
        executionProfile.notifyStart(2.0)

        // when
        Thread.sleep(300)
        val canReplay = executionProfile.canReplay(Duration.ofMinutes(10_000))

        // then
        assertThat(canReplay).isFalse()
    }
}