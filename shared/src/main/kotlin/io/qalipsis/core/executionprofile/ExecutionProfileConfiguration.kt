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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.executionprofile.CompletionMode
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.validation.constraints.Positive

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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "execution-profile")
@JsonSubTypes(
    JsonSubTypes.Type(value = RegularExecutionProfileConfiguration::class, name = "REGULAR"),
    JsonSubTypes.Type(value = AcceleratingExecutionProfileConfiguration::class, name = "ACCELERATING"),
    JsonSubTypes.Type(value = ImmediateExecutionProfileConfiguration::class, name = "IMMEDIATE"),
    JsonSubTypes.Type(value = ProgressiveVolumeExecutionProfileConfiguration::class, name = "PROGRESSING_VOLUME"),
    JsonSubTypes.Type(value = PercentageStageExecutionProfileConfiguration::class, name = "PERCENTAGE_STAGE"),
    JsonSubTypes.Type(value = StageExecutionProfileConfiguration::class, name = "STAGE"),
    JsonSubTypes.Type(value = TimeFrameExecutionProfileConfiguration::class, name = "TIME_FRAME")
)
sealed interface ExecutionProfileConfiguration {
    fun clone(): ExecutionProfileConfiguration
}

@Serializable
@SerialName("reg")
data class RegularExecutionProfileConfiguration(
    val periodInMs: Long,
    val minionsCountProLaunch: Int
) : ExecutionProfileConfiguration {

    override fun clone(): RegularExecutionProfileConfiguration {
        return copy()
    }
}

@Serializable
@SerialName("acc")
data class AcceleratingExecutionProfileConfiguration(
    val startPeriodMs: Long,
    val accelerator: Double,
    val minPeriodMs: Long,
    val minionsCountProLaunch: Int
) : ExecutionProfileConfiguration {

    override fun clone(): AcceleratingExecutionProfileConfiguration {
        return copy()
    }
}

@Serializable
@SerialName("imm")
class ImmediateExecutionProfileConfiguration : ExecutionProfileConfiguration {

    override fun clone(): ImmediateExecutionProfileConfiguration {
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }


}

@Serializable
@SerialName("prog")
data class ProgressiveVolumeExecutionProfileConfiguration(
    val periodMs: Long,
    val minionsCountProLaunchAtStart: Int,
    val multiplier: Double,
    val maxMinionsCountProLaunch: Int
) : ExecutionProfileConfiguration {

    override fun clone(): ProgressiveVolumeExecutionProfileConfiguration {
        return copy()
    }
}

@Serializable
@SerialName("percstg")
data class PercentageStageExecutionProfileConfiguration(
    val completion: CompletionMode,
    val stages: List<PercentageStage>
) : ExecutionProfileConfiguration {

    constructor(completion: CompletionMode, vararg stages: PercentageStage) : this(completion, stages.toList())

    init {
        require(stages.all { it.totalDurationMs >= it.rampUpDurationMs }) { "At least one stage has a total duration that is shorter than the ramp-up duration" }
    }

    override fun clone(): PercentageStageExecutionProfileConfiguration {
        return copy()
    }
}

@Serializable
@SerialName("stg")
data class StageExecutionProfileConfiguration(
    val completion: CompletionMode,
    val stages: List<Stage>
) : ExecutionProfileConfiguration {

    constructor(completion: CompletionMode, vararg stages: Stage) : this(completion, stages.toList())

    init {
        require(stages.all { it.totalDurationMs >= it.rampUpDurationMs }) { "At least one stage has a total duration that is shorter than the ramp-up duration" }
    }

    override fun clone(): StageExecutionProfileConfiguration {
        return copy()
    }
}

@Serializable
@SerialName("timfr")
data class TimeFrameExecutionProfileConfiguration(
    val periodInMs: Long,
    val timeFrameInMs: Long
) : ExecutionProfileConfiguration {

    override fun clone(): TimeFrameExecutionProfileConfiguration {
        return copy()
    }
}

/**
 * @property dummy required property only used to allow the serialization of the class
 */
@Serializable
@SerialName("def")
class DefaultExecutionProfileConfiguration : ExecutionProfileConfiguration {

    override fun clone(): DefaultExecutionProfileConfiguration {
        return this
    }

    override fun toString(): String {
        return "DefaultExecutionProfileConfiguration()"
    }

    override fun equals(other: Any?): Boolean {
        return other is DefaultExecutionProfileConfiguration
    }

    override fun hashCode(): Int {
        return 0
    }

}

@Serializable
@Introspected
data class Stage(

    /**
     * Total number of minions to start in the stage.
     */
    @field:Positive
    val minionsCount: Int,

    /**
     * Minions ramp up duration, in milliseconds.
     */
    @field:Positive
    val rampUpDurationMs: Long,

    /**
     * Stage duration, in milliseconds.
     */
    @field:Positive
    val totalDurationMs: Long,

    /**
     * Minimal duration between two triggering of minions start, default to 500 ms.
     */
    @field:Positive
    val resolutionMs: Long = 500
)


@Serializable
@Introspected
data class PercentageStage(

    /**
     * Percentage of minions to start in that stage.
     */
    @field:Positive
    val minionsPercentage: Double,

    /**
     * Minions ramp up duration, in milliseconds.
     */
    @field:Positive
    val rampUpDurationMs: Long,

    /**
     * Stage duration, in milliseconds.
     */
    @field:Positive
    val totalDurationMs: Long,

    /**
     * Minimal duration between two triggering of minions start, default to 500 ms.
     */
    @field:Positive
    val resolutionMs: Long = 500
)