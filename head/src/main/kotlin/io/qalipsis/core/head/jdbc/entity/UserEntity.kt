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
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.naming.NamingStrategies
import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

/**
 * User details.
 * @property username  public identified of a user (globally unique, might be the email address). A first record
 * is created into the table, with the username  “qalipsis”. This corresponds to the “system” user.
 *
 * @author Palina Bril
 */
@MappedEntity("user", namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase::class)
data class UserEntity(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    override val id: Long,

    @field:Version
    val version: Instant,

    @field:NotNull
    val creation: Instant,

    @field:NotBlank
    @field:Size(min = 1, max = 150)
    var username: String,

    @field:NotBlank
    @field:Size(min = 1, max = 150)
    var displayName: String?
) : Entity {

    constructor(
        username: String,
        displayName: String? = null
    ) : this(
        -1,
        Instant.EPOCH, Instant.now(),
        username, displayName
    )
}