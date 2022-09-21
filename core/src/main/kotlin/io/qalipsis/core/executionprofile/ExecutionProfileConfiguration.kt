/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.executionprofile

import io.qalipsis.api.executionprofile.CompletionMode
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/**
 * Configuration of the execution profile to apply to a scenario in a campaign.
 *
 * @property startOffsetMs time to wait before the first minion is executed, it should take the latency of the factories into consideration
 * @property speedFactor speed factor to apply on the execution profile, each strategy will apply it differently depending on its own implementation
 *
 * @author Eric Jessé
 */
@Serializable
@Polymorphic
sealed interface ExecutionProfileConfiguration {
    val startOffsetMs: Long
    val speedFactor: Double

    fun clone(
        startOffsetMs: Long = this.startOffsetMs,
        speedFactor: Double = this.speedFactor
    ): ExecutionProfileConfiguration
}

@Serializable
data class RegularExecutionProfileConfiguration(
    val periodInMs: Long,
    val minionsCountProLaunch: Int,
    override val startOffsetMs: Long = 3000,
    override val speedFactor: Double = 1.0
) : ExecutionProfileConfiguration {

    override fun clone(startOffsetMs: Long, speedFactor: Double): RegularExecutionProfileConfiguration {
        return copy(startOffsetMs = startOffsetMs, speedFactor = speedFactor)
    }
}

@Serializable
data class AcceleratingExecutionProfileConfiguration(
    val startPeriodMs: Long,
    val accelerator: Double,
    val minPeriodMs: Long,
    val minionsCountProLaunch: Int,
    override val startOffsetMs: Long = 3000,
    override val speedFactor: Double = 1.0
) : ExecutionProfileConfiguration {

    override fun clone(startOffsetMs: Long, speedFactor: Double): AcceleratingExecutionProfileConfiguration {
        return copy(startOffsetMs = startOffsetMs, speedFactor = speedFactor)
    }
}

@Serializable
data class ProgressiveVolumeExecutionProfileConfiguration(
    val periodMs: Long,
    val minionsCountProLaunchAtStart: Int,
    val multiplier: Double,
    val maxMinionsCountProLaunch: Int,
    override val startOffsetMs: Long = 3000,
    override val speedFactor: Double = 1.0
) : ExecutionProfileConfiguration {

    override fun clone(startOffsetMs: Long, speedFactor: Double): ProgressiveVolumeExecutionProfileConfiguration {
        return copy(startOffsetMs = startOffsetMs, speedFactor = speedFactor)
    }
}

@Serializable
data class StageExecutionProfileConfiguration(
    val stages: List<Stage>,
    val completion: CompletionMode,
    override val startOffsetMs: Long = 3000,
    override val speedFactor: Double = 1.0
) : ExecutionProfileConfiguration {

    override fun clone(startOffsetMs: Long, speedFactor: Double): StageExecutionProfileConfiguration {
        return copy(startOffsetMs = startOffsetMs, speedFactor = speedFactor)
    }
}

@Serializable
data class TimeFrameExecutionProfileConfiguration(
    val periodInMs: Long,
    val timeFrameInMs: Long,
    override val startOffsetMs: Long = 3000,
    override val speedFactor: Double = 1.0
) : ExecutionProfileConfiguration {

    override fun clone(startOffsetMs: Long, speedFactor: Double): TimeFrameExecutionProfileConfiguration {
        return copy(startOffsetMs = startOffsetMs, speedFactor = speedFactor)
    }
}

@Serializable
data class DefaultExecutionProfileConfiguration(
    override val startOffsetMs: Long = 3000,
    override val speedFactor: Double = 1.0
) : ExecutionProfileConfiguration {

    override fun clone(startOffsetMs: Long, speedFactor: Double): DefaultExecutionProfileConfiguration {
        return copy(startOffsetMs = startOffsetMs, speedFactor = speedFactor)
    }
}

@Serializable
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