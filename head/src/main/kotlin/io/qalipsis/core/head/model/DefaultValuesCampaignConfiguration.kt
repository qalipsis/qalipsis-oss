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

package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration

/**
 * External representation of the default campaign configuration.
 *
 * @author Joël Valère
 */
@Introspected
@Schema(
    name = "DefaultCampaignConfiguration",
    title = "Default values that can be used to create and validate a new campaign"
)
internal data class DefaultValuesCampaignConfiguration(
    /**
     * validation field of campaign.
     */
    override val validation: Validation
) : DefaultCampaignConfiguration

internal data class Validation(
    /**
     * maximum number of minions of a campaign, default to 10_000.
     */
    override val maxMinionsCount: Int,

    /**
     * maximum duration of a campaign's execution, default to PT1H.
     */
    override val maxExecutionDuration: Duration,

    /**
     * maximum number of scenarios to include in campaign, default to 4.
     */
    override val maxScenariosCount: Int,

    /**
     * stage validation field of a campaign.
     */
    override val stage: Stage
) : DefaultCampaignConfiguration.Validation

internal data class Stage(
    /**
     * minimum number of minions of a stage, default to 1.
     */
    override val minMinionsCount: Int,

    /**
     * maximum number of minions of a stage, default to validation maxMinionsCount field.
     */
    override val maxMinionsCount: Int?,

    /**
     * minimum resolution of a stage, default to PT0.5S.
     */
    override val minResolution: Duration,

    /**
     * maximum resolution of a stage, default to PT5M.
     */
    override val maxResolution: Duration,

    /**
     * minimum duration of a stage, default to PT5S.
     */
    override val minDuration: Duration,

    /**
     * maximum duration of a stage, default to validation maxExecutionDuration field.
     */
    override val maxDuration: Duration?,

    /**
     * minimum start duration of a stage, default to minDuration.
     */
    override var minStartDuration: Duration?,

    /**
     * maximum start duration of a stage, default to maxDuration.
     */
    override var maxStartDuration: Duration?
) : DefaultCampaignConfiguration.Validation.Stage {
    init {
        maxStartDuration = maxStartDuration ?: maxDuration
        minStartDuration = minStartDuration ?: minDuration
    }
}