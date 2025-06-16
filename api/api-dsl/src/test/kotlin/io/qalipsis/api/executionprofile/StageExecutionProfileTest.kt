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

package io.qalipsis.api.executionprofile

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.spyk
import io.qalipsis.test.mockk.verifyExactly
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Svetlana Paliashchuk
 */
internal class StageExecutionProfileTest {

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

        val iterator = executionProfile.iterator(1000, 1.0)

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
    internal fun `should provide constant count within each stage and restrict to the total of minions configured by stages`() {
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
    internal fun `should provide constant count within each stage applying the factor`() {
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

        val iterator = spyk(executionProfile.iterator(100, 2.0))

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
    internal fun `should provide constant count within each stage as long as there are minions to start`() {
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