package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.core.configuration.ExecutionEnvironments
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

/**
 * Configuration of a campaign to start. When the environment [ExecutionEnvironments.AUTOSTART] is active,
 * a bean of that type is created.
 *
 * @property id technical identifier of the campaign
 * @property minionsCountPerScenario when set to a non-null value, specifies the number of minions to create for each scenario
 * @property minionsFactor when minionsCountPerCampaign is not set, the factor applies to the default minions count of each scenario
 * @property speedFactor speed factor for the ramp-up
 * @property startOffsetMs offset (in milliseconds) to apply to the ramp-up directive to be sure all the directives for all the scenarios are received when it really comes to start
 * @property scenarios identifiers of the scenarios to include in the campaign
 *
 * @author Eric Jessé
 */
@Requires(env = [ExecutionEnvironments.AUTOSTART])
@ConfigurationProperties("campaign")
interface AutostartCampaignConfiguration {

    @get:NotBlank
    val id: String

    @get:PositiveOrZero
    val minionsCountPerScenario: Int

    @get:Positive
    val minionsFactor: Double

    @get:Positive
    val speedFactor: Double

    @get:Positive
    val startOffsetMs: Long

    val scenarios: List<ScenarioId>
}