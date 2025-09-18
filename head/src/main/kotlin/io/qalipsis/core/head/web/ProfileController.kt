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
import io.micronaut.context.annotation.Requirements
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
import io.qalipsis.core.head.web.annotation.NoOpTenantBinder

/**
 * API to provide details about the current user.
 *
 * @author Eric Jessé
 */
@Validated
@Version("1.0")
@Requirements(
    Requires(bean = NoOpTenantBinder::class),
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
)
@Controller
class ProfileController {

    @Get("\${server.api-root}/users/profile")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Timed("users-profile")
    suspend fun profile(): Profile<*> {
        return Defaults.PROFILE
    }

    @Get("\${server.api-root}/users/permissions")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Timed("users-permissions")
    suspend fun permissions(authentication: Authentication): Collection<String> {
        return authentication.roles.sorted()
    }
}