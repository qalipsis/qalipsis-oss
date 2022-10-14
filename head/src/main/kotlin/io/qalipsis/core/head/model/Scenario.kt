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
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * External representation of a scenario.
 *
 * @author Eric Jessé
 */
@Introspected
@Schema(
    name = "Scenario details",
    title = "Details of a scenario to execute in campaigns"
)
internal data class Scenario(
    @field:Schema(description = "Last stored update of the scenario", required = true)
    val version: Instant,

    @field:NotBlank
    @field:Size(min = 2, max = 255)
    @field:Schema(description = "Display name of the scenario", required = true, example = "my-first-scenario")
    val name: String,

    @field:Positive
    @field:Max(1000000)
    @field:Schema(description = "Number of minions executed in the scenario", required = true, example = "100")
    val minionsCount: Int
)
