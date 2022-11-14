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

package io.qalipsis.core.head.jdbc.entity

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.DataType
import io.micronaut.data.model.naming.NamingStrategies
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.model.CampaignConfiguration
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

/**
 * Details of a campaign.
 *
 * @property id internal database ID
 * @property version version of the entity
 * @property tenantId internal database ID of the tenant owning the campaign
 * @property key unique public identifier of the campaign in the tenant, generated
 * @property name display name of the campaign
 * @property speedFactor speed factor to apply on the execution profile, each strategy will apply it differently depending on its own implementation
 * @property scheduledMinions the number of minions planned to be executed over all the scenarios of the campaign
 * @property timeout instant when the campaign should be automatically aborted for timeout
 * @property hardTimeout specifies whether the timeout should be applied in hard mode (generates a failure) or soft (just shutdown everything).
 * @property start when the campaign was started
 * @property end when the campaign was completed, successfully or not
 * @property result overall execution status of the campaign
 * @property configurer internal database ID of the user that started the campaign
 *
 * @author Eric Jessé
 */
@MappedEntity("campaign", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
internal data class CampaignEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,
    @field:Version
    val version: Instant,
    val tenantId: Long,
    val creation: Instant = Instant.now(),
    @field:NotBlank
    @field:Size(min = 5, max = 80)
    val key: String,
    @field:Size(max = 300)
    val name: String,
    @field:Size(max = 300)
    val speedFactor: Double,
    val scheduledMinions: Int,
    val timeout: Instant? = null,
    val hardTimeout: Boolean? = null,
    val start: Instant?,
    val end: Instant?,
    val result: ExecutionStatus?,
    val configurer: Long,
    val aborter: Long? = null,
    @field:TypeDef(type = DataType.JSON)
    val configuration: CampaignConfiguration? = null
) : Entity {

    constructor(
        tenantId: Long = -1,
        creation: Instant = Instant.now(),
        key: String,
        name: String,
        speedFactor: Double = 1.0,
        scheduledMinions: Int,
        timeout: Instant? = null,
        hardTimeout: Boolean = false,
        start: Instant? = null,
        end: Instant? = null,
        result: ExecutionStatus? = null,
        configurer: Long,
        aborter: Long? = null,
        configuration: CampaignConfiguration? = null
    ) : this(
        id = -1,
        creation = creation,
        version = Instant.EPOCH,
        tenantId = tenantId,
        key = key,
        name = name,
        speedFactor = speedFactor,
        scheduledMinions = scheduledMinions,
        timeout = timeout,
        hardTimeout = hardTimeout,
        start = start,
        end = end,
        result = result,
        configurer = configurer,
        aborter = aborter,
        configuration = configuration
    )
}