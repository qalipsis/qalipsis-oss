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

package io.qalipsis.core.head.web

import io.micrometer.core.annotation.Timed
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.Profile
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement

/**
 * API to provide details about the current user.
 *
 * @author Eric Jessé
 */
@Validated
@Version("1.0")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Controller
internal class ProfileController {

    @Operation(
        summary = "User profile",
        description = "Provide the different details of the current user",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of available zones"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Get("/users/profile")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Timed("users-profile")
    suspend fun profile(authentication: Authentication): Profile {
        return Defaults.PROFILE
    }

    @Operation(
        summary = "Permissions",
        description = "Provide the list of permissions, that are assigned to the current user in the current context",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of available zones"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    @Get("/users/permissions")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Timed("users-permissions")
    suspend fun permissions(authentication: Authentication): Collection<String> {
        return authentication.roles
    }
}