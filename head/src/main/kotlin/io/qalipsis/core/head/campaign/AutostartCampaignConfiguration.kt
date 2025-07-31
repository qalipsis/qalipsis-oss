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

package io.qalipsis.core.head.campaign

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.context.CampaignKey
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
 * @property speedFactor speed factor for the execution profile
 * @property startOffset offset (in milliseconds) to apply to the ramp-up directive to be sure all the directives for all the scenarios are received when it really comes to start
 *
 * @author Eric Jessé
 */
@Requires(env = [ExecutionEnvironments.AUTOSTART])
@ConfigurationProperties("campaign")
class AutostartCampaignConfiguration {

    @get:NotBlank
    lateinit var name: String

    @get:Positive
    var requiredFactories: Int = 1

    @get:PositiveDuration
    @get:Bindable(defaultValue = "PT500MS")
    var triggerOffset: Duration = Duration.ofMillis(500)

    @get:PositiveOrZero
    var minionsCountPerScenario: Int = 1

    @get:Positive
    var minionsFactor: Double = 1.0

    @get:Positive
    var speedFactor: Double = 1.0

    @get:PositiveDuration
    @get:Bindable(defaultValue = "1s")
    var startOffset: Duration = Duration.ofMillis(1_000)

    lateinit var generatedKey: CampaignKey
}