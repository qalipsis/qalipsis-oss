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

import io.qalipsis.api.scenario.ExecutionProfileSpecification
import java.time.Duration
import kotlin.math.ceil

/**
 * Execution profile strategy to start the minions in different linear stages, possibly split by “plateaus”.
 *
 * The global speed factor applies on the constant period, reducing or increasing it.
 *
 * @author Svetlana Paliashchuk
 */
data class StageExecutionProfile(
    private val stages: List<Stage>,
    private val completion: CompletionMode
) : ExecutionProfile {

    override fun iterator(totalMinionsCount: Int, speedFactor: Double) =
        StageExecutionProfileIterator(totalMinionsCount, speedFactor, stages, completion)

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
        private val totalMinionsCount: Int,
        private val speedFactor: Double,
        private val stages: Collection<Stage>,
        private val completion: CompletionMode
    ) : ExecutionProfileIterator {

        private var remainingMinions = totalMinionsCount

        private var nextStart = 0.0

        private val startingLines = getStartingLines().toMutableList()

        private fun getStartingLines(): List<MinionsStartingLine> {
            return stages.flatMap { stage ->
                val linesCount = stage.rampUpDurationMs / stage.resolutionMs
                val delayBetweenLines = stage.resolutionMs / speedFactor
                val minionsByLine = ceil((stage.minionsCount / linesCount).toDouble()).toInt()
                var remainingMinionsInCurrentStage = stage.minionsCount.coerceAtMost(remainingMinions)

                val stageResult =
                    (1..linesCount.coerceAtMost((remainingMinionsInCurrentStage / minionsByLine).toLong()))
                        .takeWhile {
                            remainingMinionsInCurrentStage > 0 && remainingMinions > 0
                        }.map {
                            nextStart += delayBetweenLines
                            val minionsCount =
                                minionsByLine.coerceAtMost(remainingMinionsInCurrentStage)
                                    .coerceAtMost(remainingMinions)
                            remainingMinionsInCurrentStage -= minionsCount
                            remainingMinions -= minionsCount

                            MinionsStartingLine(minionsCount, nextStart.toLong())
                        }
                nextStart += (stage.totalDurationMs / speedFactor).toLong()
                stageResult
            }
        }

        override fun next(): MinionsStartingLine {
            return startingLines.removeFirst()
        }

        override fun hasNext(): Boolean {
            return startingLines.count() > 0
        }
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

/**
 * A strategy configuration indicator determining how to end the scenario.
 */
enum class CompletionMode {

    /**
     * After the latest stage was totally completed, all the minions are forced to stop.
     */
    FORCED,

    /**
     * After the latest stage was totally completed, is it waited that all the minions are naturally completed.
     */
    GRACEFUL
}

/**
 * Starts the minions in different linear stages, possibly split by “plateaus”.
 */
fun ExecutionProfileSpecification.stages(
    completion: CompletionMode = CompletionMode.FORCED,
    stages: Stages.() -> Unit
) {
    val stagesBuilder = StagesBuilder()
    stagesBuilder.stages()
    strategy(
        StageExecutionProfile(
            stages = stagesBuilder.stages,
            completion
        )
    )
}

/**
 * Interface to describe the [Stage]s.
 */
interface Stages {

    /**
     * Defines the [Stage] receiving the duration parameters in milliseconds as [Long].
     */
    fun stage(minionsCount: Int, rampUpDurationMs: Long, totalDurationMs: Long, resolutionMs: Long)

    /**
     * Defines the [Stage] receiving the duration parameters as [Duration].
     */
    fun stage(minionsCount: Int, rampUpDuration: Duration, totalDuration: Duration, resolution: Duration)
}

/**
 * Default implementation of [Stages] interface.
 */
private class StagesBuilder : Stages {

    val stages = mutableListOf<Stage>()

    override fun stage(minionsCount: Int, rampUpDurationMs: Long, totalDurationMs: Long, resolutionMs: Long) {
        stages.add(Stage(minionsCount, rampUpDurationMs, totalDurationMs, resolutionMs))
    }

    override fun stage(minionsCount: Int, rampUpDuration: Duration, totalDuration: Duration, resolution: Duration) {
        stages.add(Stage(minionsCount, rampUpDuration.toMillis(), totalDuration.toMillis(), resolution.toMillis()))
    }

}