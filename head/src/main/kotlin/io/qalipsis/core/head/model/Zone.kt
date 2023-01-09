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
import java.net.URL
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Details of a zone to return by REST.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Zone",
    title = "Zone of QALIPSIS",
    description = "Details of a zone in scenarios"
)
internal data class Zone(

    @field:Schema(description = "A unique identifier for the zone")
    @field:NotBlank
    @field:Size(min = 2, max = 3)
    val key: String,

    @field:Schema(description = "A complete name of the zone, generally the country")
    @field:NotBlank
    @field:Size(min = 3, max = 20)
    val title: String,

    @field:Schema(description = "A more detailed definition of the zone, generally the region, datacenter and the localization details")
    @field:NotBlank
    @field:Size(min = 3, max = 50)
    val description: String? = null,

    @field:Schema(description = "Image URL to display for the zone")
    val image: URL? = null
)