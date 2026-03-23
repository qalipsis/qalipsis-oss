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

package io.qalipsis.core.head.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import io.qalipsis.api.constraints.PositiveDuration
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Default values that can be used to create a new campaign.
 *
 * @author Joël Valère
 */

@ConfigurationProperties("campaign.configuration")
class DefaultCampaignConfiguration {

    var validation: Validation = Validation()

    @ConfigurationProperties("validation")
    class Validation {

        @field:Positive
        var maxMinionsCount: Int = 10000

        @field:PositiveDuration
        var maxExecutionDuration: Duration = Duration.ofHours(1)

        @field:Positive
        var maxScenariosCount: Int = 4

        var stage: Stage = Stage()

        @ConfigurationProperties("stage")
        class Stage {

            @field:Positive
            var minMinionsCount: Int = 1

            var maxMinionsCount: Int? = null

            @field:PositiveDuration
            var minResolution: Duration = Duration.ofMillis(500)

            @field:PositiveDuration
            var maxResolution: Duration = Duration.ofMinutes(5)

            @field:PositiveDuration
            var minDuration: Duration = Duration.ofSeconds(5)

            @field:PositiveDuration
            var maxDuration: Duration? = null

            @field:PositiveDuration
            var minStartDuration: Duration? = null

            @field:PositiveDuration
            var maxStartDuration: Duration? = null
        }
    }
}
