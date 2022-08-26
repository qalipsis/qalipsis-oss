package io.qalipsis.core.head.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.bind.annotation.Bindable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Interface to fetch the default values that can be used to create a new campaign.
 *
 * @author Joël Valère
 */

@ConfigurationProperties("campaign.configuration")
internal interface DefaultCampaignConfiguration {

    val validation: Validation

    @ConfigurationProperties("validation")
    interface Validation {

        @get:Positive
        @get:Bindable(defaultValue = "10000")
        val maxMinionsCount: Int

        @get:Positive
        @get:Bindable(defaultValue = "PT1H")
        val maxExecutionDuration: Duration

        @get:Positive
        @get:Bindable(defaultValue = "4")
        val maxScenariosCount: Int

        val stage: Stage

        @ConfigurationProperties("stage")
        interface Stage {

            @get:Positive
            @get:Bindable(defaultValue = "1")
            val minMinionsCount: Int

            @get:Nullable
            val maxMinionsCount: Int?

            @get:Positive
            @get:Bindable(defaultValue = "PT0.5S")
            val minResolution: Duration

            @get:Positive
            @get:Bindable(defaultValue = "PT5M")
            val maxResolution: Duration

            @get:Positive
            @get:Bindable(defaultValue = "PT5S")
            val minDuration: Duration

            @get:Nullable
            val maxDuration: Duration?

            @get:Nullable
            val minStartDuration: Duration?

            @get:Nullable
            val maxStartDuration: Duration?
        }
    }
}

@Introspected
@Schema(
    name = "DefaultCampaignConfiguration",
    title = "Default values that can be used to create and validate a new campaign"
)
internal data class DefaultValuesCampaignConfiguration(
    /**
     * validation field of campaign.
     */
    val validation: Validation
)

internal data class Validation(
    /**
     * maximum number of minions of a campaign, default to 10_000.
     */
    val maxMinionsCount: Int,

    /**
     * maximum duration of a campaign's execution, default to PT1H.
     */
    val maxExecutionDuration: Duration,

    /**
     * maximum number of scenarios to include in campaign, default to 4.
     */
    val maxScenariosCount: Int,

    /**
     * stage validation field of a campaign.
     */
    val stage: Stage
)

internal data class Stage(
    /**
     * minimum number of minions of a stage, default to 1.
     */
    val minMinionsCount: Int,

    /**
     * maximum number of minions of a stage, default to validation maxMinionsCount field.
     */
    val maxMinionsCount: Int?,

    /**
     * minimum resolution of a stage, default to PT0.5S.
     */
    val minResolution: Duration,

    /**
     * maximum resolution of a stage, default to PT5M.
     */
    val maxResolution: Duration,

    /**
     * minimum duration of a stage, default to PT5S.
     */
    val minDuration: Duration,

    /**
     * maximum duration of a stage, default to validation maxExecutionDuration field.
     */
    val maxDuration: Duration?,

    /**
     * minimum start duration of a stage, default to minDuration.
     */
    var minStartDuration: Duration?,

    /**
     * maximum start duration of a stage, default to maxDuration.
     */
    var maxStartDuration: Duration?
){
    init {
        maxStartDuration = maxStartDuration ?: maxDuration
        minStartDuration = minStartDuration ?: minDuration
    }
}