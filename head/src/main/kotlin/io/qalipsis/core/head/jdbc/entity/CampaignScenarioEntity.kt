/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive
import javax.validation.constraints.Size

/**
 * Details of a campaign.
 *
 * @author Eric Jessé
 */
@MappedEntity("campaign_scenario", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class CampaignScenarioEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val campaignId: Long,
    @field:NotBlank
    @field:Size(min = 2, max = 255)
    val name: String,
    @field:Positive
    @field:Max(1000000)
    val minionsCount: Int,
    val start: Instant?,
    val end: Instant?,
) : Entity {

    constructor(
        campaignId: Long,
        name: String,
        start: Instant? = null,
        end: Instant? = null,
        minionsCount: Int
    ) : this(
        id = -1,
        version = Instant.EPOCH,
        campaignId = campaignId,
        name = name,
        minionsCount = minionsCount,
        start = start,
        end = end
    )
}