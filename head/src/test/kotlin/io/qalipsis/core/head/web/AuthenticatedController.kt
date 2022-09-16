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

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.web.annotation.Tenant

/**
 * Controller used for testing the security resolution in different use cases.
 *
 * @author Eric Jessé
 */
@Controller("/")
internal class AuthenticatedController {

    @Get("/unsecure")
    @Secured(SecurityRule.IS_ANONYMOUS)
    suspend fun unsecure(
        @Tenant tenant: String,
        authentication: Authentication
    ): CallResult {
        return CallResult(
            tenant,
            authentication.name,
            authentication.roles,
            authentication.attributes
        )
    }

    @Get("/secure")
    @Secured(value = [Permissions.WRITE_CAMPAIGN])
    suspend fun secure(
        @Tenant tenant: String,
        authentication: Authentication
    ): CallResult {
        return CallResult(
            tenant,
            authentication.name,
            authentication.roles,
            authentication.attributes
        )
    }

    @Introspected
    data class CallResult(
        val tenant: String,
        val name: String,
        val roles: Collection<String>,
        val attributes: Map<String, Any>
    )
}