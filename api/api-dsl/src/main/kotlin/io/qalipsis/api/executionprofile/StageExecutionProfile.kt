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

import io.qalipsis.api.executionprofile.CompletionMode.HARD
import io.qalipsis.api.logging.LoggerHelper.logger
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/**
 * Execution profile strategy to start the minions in different linear stages, possibly split by “plateaus”.
 *
 * The global speed factor applies on the constant period, reducing or increasing it.
 *
 * @author Svetlana Paliashchuk
 */
data class StageExecutionProfile(
    private val completion: CompletionMode,
    private val stages: List<Stage>
) : ExecutionProfile {

    /**
     * Theoretical end of the latest stage since epoch (in ms).
     */
    private var latestStageEnd: Long = Long.MIN_VALUE

    override fun notifyStart(speedFactor: Double) {
        if (latestStageEnd == Long.MIN_VALUE) {
            // Calculates the end of the latest stage.
            val delayUntilEnd = stages.sumOf { it.totalDurationMs } / speedFactor
            latestStageEnd = Instant.now().plusMillis(delayUntilEnd.toLong()).toEpochMilli()
            log.debug { "The latest stage ends at $latestStageEnd" }
            super.notifyStart(speedFactor)
        }
    }

    override fun iterator(totalMinionsCount: Int, speedFactor: Double): StageExecutionProfileIterator {
        log.debug { "Stages of the profile: $stages" }
        return StageExecutionProfileIterator(totalMinionsCount, speedFactor, stages)
    }

    override fun canReplay(minionExecutionDuration: Duration): Boolean {
        return if (completion == HARD) {
            // If the remaining time until the end is bigger that the time of the latest execution of the minion,
            // it is let being restarted. The running minion will later be interrupted.
            (latestStageEnd - System.currentTimeMillis()) > minionExecutionDuration.toMillis()
        } else {
            // If the completion is GRACEFUL, we start the minions as long as the end of the latest stage is not reached.
            latestStageEnd > System.currentTimeMillis()
        }.also {
            log.trace {
                val choice = if (it) "" else "not"
                val end = Instant.ofEpochMilli(latestStageEnd)
                "A minion running for $minionExecutionDuration can$choice be replayed until $end in the completion mode $completion"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StageExecutionProfile

        if (stages != other.stages) return false
        if (completion != other.completion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stages.hashCode()
        result = 31 * result + completion.hashCode()
        return result
    }

    inner class StageExecutionProfileIterator(
        private var remainingMinionsGlobally: Int,
        private val speedFactor: Double,
        private val stages: Collection<Stage>
    ) : ExecutionProfileIterator {

        private val startingLines = getStartingLines().toMutableList()

        private fun getStartingLines(): List<MinionsStartingLine> {
            var stageStartOffset = 0L

            return stages.flatMapIndexed { stageIndex, stage ->
                val linesCount = stage.rampUpDurationMs / stage.resolutionMs
                var remainingMinionsForCurrentStage = stage.minionsCount.coerceAtMost(remainingMinionsGlobally)
                log.trace { "Stage $stageIndex: $remainingMinionsForCurrentStage remaining minions" }
                val minionsByLine = ceil((stage.minionsCount.toDouble() / linesCount)).toInt()
                log.trace { "Stage $stageIndex: $minionsByLine minions by starting line" }
                val maxStartingLines = ceil(remainingMinionsForCurrentStage.toDouble() / minionsByLine).toLong()
                val delayBetweenLines = (stage.resolutionMs / speedFactor).toLong()
                var stageStartingClockTime = 0L

                val stageResult = (1..linesCount.coerceAtMost(maxStartingLines))
                    .takeWhile {
                        remainingMinionsForCurrentStage > 0 && remainingMinionsGlobally > 0
                    }.mapIndexed { index, _ ->
                        log.trace { "Stage $stageIndex, starting line $index: $remainingMinionsForCurrentStage remaining minions in stage, $remainingMinionsGlobally remaining minions globally" }
                        val minionsCountToStart =
                            minionsByLine.coerceAtMost(remainingMinionsForCurrentStage)
                                .coerceAtMost(remainingMinionsGlobally)
                        log.trace { "Stage $stageIndex, starting line $index: $minionsCountToStart minions to start" }
                        remainingMinionsForCurrentStage -= minionsCountToStart
                        remainingMinionsGlobally -= minionsCountToStart

                        val nextStart = if (index == 0) stageStartOffset else delayBetweenLines
                        stageStartingClockTime += delayBetweenLines
                        MinionsStartingLine(minionsCountToStart, nextStart)
                    }.toList()

                stageStartOffset = (stage.totalDurationMs / speedFactor).toLong() - stageStartingClockTime
                stageResult
            }.also {
                if (log.isTraceEnabled) {
                    log.trace { "Starting lines (count: ${it.size}): $it" }
                } else {
                    log.debug { "${it.size} starting lines were set, ending in ${it.last().offsetMs} milliseconds" }
                }
            }
        }

        override fun next(): MinionsStartingLine {
            return startingLines.removeFirst()
        }

        override fun hasNext(): Boolean {
            return startingLines.isNotEmpty()
        }
    }

    private companion object {
        val log = logger()
    }
}

data class Stage(

    /**
     * Total number of minions to start in the stage.
     */
    val minionsCount: Int,

    /**
     * Minions ramp up duration, in milliseconds.
     */
    val rampUpDurationMs: Long,

    /**
     * Stage duration, in milliseconds.
     */
    val totalDurationMs: Long,

    /**
     * Minimal duration between two triggering of minions start, default to 500 ms.
     */
    val resolutionMs: Long = 500
)

