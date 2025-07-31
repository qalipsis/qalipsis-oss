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

package io.qalipsis.cluster.security

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton

/**
 * Model to send the configuration information required for the frontend.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "SecurityConfiguration",
    title = "Details of the SecurityConfiguration necessary for the frontend"
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "strategy",
    defaultImpl = DisabledSecurityConfiguration::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DisabledSecurityConfiguration::class, name = "DISABLED")
)
interface SecurityConfiguration

@Singleton
@Requires(missingBeans = [SecurityConfiguration::class])
@Introspected
class DisabledSecurityConfiguration : SecurityConfiguration
