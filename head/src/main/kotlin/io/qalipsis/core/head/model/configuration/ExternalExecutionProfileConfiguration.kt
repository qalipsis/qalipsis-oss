package io.qalipsis.core.head.model.configuration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.executionprofile.CompletionMode
import io.qalipsis.core.executionprofile.PercentageStage
import io.qalipsis.core.executionprofile.Stage
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Positive

/**
 * Interface of execution profile configuration.
 *
 * @author Svetlana Paliashchuk
 */
@Introspected
@Schema(
    name = "ExecutionProfileConfiguration",
    title = "Details of the ExecutionProfileConfiguration to choose the execution profile",
    allOf = [
        AcceleratingExternalExecutionProfileConfiguration::class,
        ImmediatelyExternalExecutionProfileConfiguration::class,
        RegularExternalExecutionProfileConfiguration::class,
        ProgressiveVolumeExternalExecutionProfileConfiguration::class,
        StageExternalExecutionProfileConfiguration::class,
        PercentageStageExternalExecutionProfileConfiguration::class,
        TimeFrameExternalExecutionProfileConfiguration::class,
    ]
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "profile"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RegularExternalExecutionProfileConfiguration::class, name = "REGULAR"),
    JsonSubTypes.Type(value = ImmediatelyExternalExecutionProfileConfiguration::class, name = "IMMEDIATELY"),
    JsonSubTypes.Type(value = AcceleratingExternalExecutionProfileConfiguration::class, name = "ACCELERATING"),
    JsonSubTypes.Type(
        value = ProgressiveVolumeExternalExecutionProfileConfiguration::class,
        name = "PROGRESSING_VOLUME"
    ),
    JsonSubTypes.Type(value = PercentageStageExternalExecutionProfileConfiguration::class, name = "PERCENTAGE_STAGE"),
    JsonSubTypes.Type(value = StageExternalExecutionProfileConfiguration::class, name = "STAGE"),
    JsonSubTypes.Type(value = TimeFrameExternalExecutionProfileConfiguration::class, name = "TIME_FRAME")
)
interface ExternalExecutionProfileConfiguration {
    val type: ExecutionProfileType
}

@Introspected
@Schema(
    name = "RegularExternalExecutionProfileConfiguration",
    title = "Details of the RegularExternalExecutionProfileConfiguration to create the execution profile"
)
internal class RegularExternalExecutionProfileConfiguration(
    @field:Schema(description = "Period to apply between each launch, in milliseconds", required = true)
    @field:Positive
    val periodInMs: Long,
    @field:Schema(description = "Number of minions to start at each launch", required = true)
    @field:Positive
    val minionsCountProLaunch: Int
) : ExternalExecutionProfileConfiguration {

    override val type: ExecutionProfileType = TYPE

    companion object {
        val TYPE = ExecutionProfileType.REGULAR
    }
}

@Introspected
@Schema(
    name = "AcceleratingExternalExecutionProfileConfiguration",
    title = "Details of the AcceleratingExternalExecutionProfileConfiguration to create the execution profile"
)
internal class AcceleratingExternalExecutionProfileConfiguration(
    @field:Schema(description = "Period between launch to apply at start, in milliseconds", required = true)
    @field:Positive
    val startPeriodMs: Long,
    @field:Schema(description = "Accelerator factor to reduce the period at each launch", required = true)
    @field:Positive
    val accelerator: Double,
    @field:Schema(description = "Minimal period between launches, in milliseconds", required = true)
    @field:Positive
    val minPeriodMs: Long,
    @field:Schema(description = "Number of minions to start at each launch", required = true)
    @field:Positive
    val minionsCountProLaunch: Int
) : ExternalExecutionProfileConfiguration {

    override val type: ExecutionProfileType = TYPE

    companion object {
        val TYPE = ExecutionProfileType.ACCELERATING
    }
}


@Introspected
@Schema(
    name = "ImmediatelyExternalExecutionProfileConfiguration",
    title = "Details of the ImmediatelyExternalExecutionProfileConfiguration to create the execution profile"
)
internal class ImmediatelyExternalExecutionProfileConfiguration() : ExternalExecutionProfileConfiguration {

    override val type: ExecutionProfileType = TYPE

    companion object {
        val TYPE = ExecutionProfileType.IMMEDIATELY
    }
}

@Introspected
@Schema(
    name = "ProgressiveVolumeExternalExecutionProfileConfiguration",
    title = "Details of the ProgressiveVolumeExternalExecutionProfileConfiguration to create the execution profile"
)
internal class ProgressiveVolumeExternalExecutionProfileConfiguration(
    @field:Schema(description = "Period to apply between each launch, in milliseconds", required = true)
    @field:Positive
    val periodMs: Long,
    @field:Schema(description = "Number of minions to start at each launch", required = true)
    @field:Positive
    val minionsCountProLaunchAtStart: Int,
    @field:Schema(description = "Factor to increase the number of minions pro launch", required = true)
    @field:Positive
    val multiplier: Double,
    @field:Schema(description = "Maximal number of minions to start at each launch", required = true)
    @field:Positive
    val maxMinionsCountProLaunch: Int
) : ExternalExecutionProfileConfiguration {

    override val type: ExecutionProfileType = TYPE

    companion object {
        val TYPE = ExecutionProfileType.PROGRESSING_VOLUME
    }
}

@Introspected
@Schema(
    name = "PercentageStageExternalExecutionProfileConfiguration",
    title = "Details of the PercentageStageExternalExecutionProfileConfiguration to create the execution profile"
)
internal class PercentageStageExternalExecutionProfileConfiguration(
    @field:Schema(description = "Phase of the scenario execution", required = true)
    @field:NotEmpty
    @field:Valid
    val stages: List<@Valid PercentageStage>,
    @field:Schema(
        description = "Indicator to determine how to end the scenario",
        required = true,
        defaultValue = "GRACEFUL"
    )
    val completion: CompletionMode
) : ExternalExecutionProfileConfiguration {

    override val type: ExecutionProfileType = TYPE

    companion object {
        val TYPE = ExecutionProfileType.PERCENTAGE_STAGE
    }
}

@Introspected
@Schema(
    name = "StageExternalExecutionProfileConfiguration",
    title = "Details of the StageExternalExecutionProfileConfiguration to create the execution profile"
)
internal class StageExternalExecutionProfileConfiguration(
    @field:Schema(description = "Phase of the scenario execution", required = true)
    @field:NotEmpty
    @field:Valid
    val stages: List<@Valid Stage>,
    @field:Schema(
        description = "Indicator to determine how to end the scenario",
        required = true,
        defaultValue = "GRACEFUL"
    )
    val completion: CompletionMode
) : ExternalExecutionProfileConfiguration {

    override val type: ExecutionProfileType = TYPE

    companion object {
        val TYPE = ExecutionProfileType.STAGE
    }
}

@Introspected
@Schema(
    name = "TimeFrameExternalExecutionProfileConfiguration",
    title = "Details of the TimeFrameExternalExecutionProfileConfiguration to create the execution profile"
)
internal class TimeFrameExternalExecutionProfileConfiguration(
    @field:Schema(description = "Period to apply between each launch, in milliseconds", required = true)
    @field:Positive
    val periodInMs: Long,
    @field:Schema(description = "Time frame limit, in milliseconds", required = true)
    @field:Positive
    val timeFrameInMs: Long
) : ExternalExecutionProfileConfiguration {

    override val type: ExecutionProfileType = TYPE

    companion object {
        val TYPE = ExecutionProfileType.TIME_FRAME
    }
}

/**
 * The type of execution profile.
 *
 * @author Svetlana Paliashchuk
 */
@Introspected
enum class ExecutionProfileType {
    REGULAR, ACCELERATING, PROGRESSING_VOLUME, STAGE, TIME_FRAME, PERCENTAGE_STAGE, IMMEDIATELY
}