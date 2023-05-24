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

import io.micronaut.context.annotation.Infrastructure
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.ServerAuthentication
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import java.util.Optional

/**
 * Binder to inject the default user when no security is active.
 */
@Infrastructure
@Requirements(Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]))
internal class NoSecurityAuthenticationBinder : TypedRequestArgumentBinder<Authentication> {
    override fun bind(
        context: ArgumentConversionContext<Authentication>,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<Authentication> {
        return ArgumentBinder.BindingResult {
            Optional.of(
                ServerAuthentication(
                    Defaults.USER,
                    Permissions.ALL_PERMISSIONS,
                    emptyMap()
                )
            )
        }
    }

    override fun argumentType(): Argument<Authentication> {
        return Argument.of(Authentication::class.java)
    }

}