package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.core.configuration.ExecutionEnvironments
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

/**
 * Configuration of a campaign to start. When the environment [ExecutionEnvironments.AUTOSTART] is active,
 * a bean of that type is created.
 *
 * @property name technical identifier of the campaign
 * @property requiredFactories number of fact
 * @property triggerOffset time to wait before the campaign is triggered, to let the factories process the handshake response
 * @property minionsCountPerScenario when set to a non-null value, specifies the number of minions to create for each scenario
 * @property minionsFactor when minionsCountPerCampaign is not set, the factor applies to the default minions count of each scenario
 * @property speedFactor speed factor for the ramp-up
 * @property startOffset offset (in milliseconds) to apply to the ramp-up directive to be sure all the directives for all the scenarios are received when it really comes to start
 *
 * @author Eric Jessé
 */
@Requires(env = [ExecutionEnvironments.AUTOSTART])
@ConfigurationProperties("campaign")
internal interface AutostartCampaignConfiguration {

    @get:Bindable(defaultValue = "")
    val tenant: String

    @get:NotBlank
    val name: String

    @get:Positive
    @get:Bindable(defaultValue = "1")
    val requiredFactories: Int

    @get:Positive
    @get:Bindable(defaultValue = "500ms")
    val triggerOffset: Duration

    @get:PositiveOrZero
    val minionsCountPerScenario: Int

    @get:Positive
    @get:Bindable(defaultValue = "1")
    val minionsFactor: Double

    @get:Positive
    @get:Bindable(defaultValue = "1")
    val speedFactor: Double

    @get:Positive
    @get:Bindable(defaultValue = "1s")
    val startOffset: Duration
}