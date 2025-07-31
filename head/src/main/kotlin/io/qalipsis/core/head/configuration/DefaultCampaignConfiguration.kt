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
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.api.constraints.PositiveDuration
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Interface to fetch the default values that can be used to create a new campaign.
 *
 * @author Joël Valère
 */

@ConfigurationProperties("campaign.configuration")
interface DefaultCampaignConfiguration {

    val validation: Validation

    @ConfigurationProperties("validation")
    interface Validation {

        @get:Positive
        @get:Bindable(defaultValue = "10000")
        val maxMinionsCount: Int

        @get:PositiveDuration
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

            @get:PositiveDuration
            @get:Bindable(defaultValue = "PT0.5S")
            val minResolution: Duration

            @get:PositiveDuration
            @get:Bindable(defaultValue = "PT5M")
            val maxResolution: Duration

            @get:PositiveDuration
            @get:Bindable(defaultValue = "PT5S")
            val minDuration: Duration

            @get:Nullable
            @get:PositiveDuration
            val maxDuration: Duration?

            @get:Nullable
            @get:PositiveDuration
            val minStartDuration: Duration?

            @get:Nullable
            @get:PositiveDuration
            val maxStartDuration: Duration?
        }
    }
}