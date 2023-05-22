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

package io.qalipsis.core.head.web.annotation

import io.micronaut.context.annotation.Infrastructure
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import io.qalipsis.cluster.security.Tenant
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import java.util.*


/**
 * Binder to inject the default tenant reference when no security is active.
 */
@Infrastructure
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
internal class NoOpTenantBinder : AnnotatedRequestArgumentBinder<Tenant, String> {

    override fun bind(
        context: ArgumentConversionContext<String>,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<String> {
        return ArgumentBinder.BindingResult<String> { Optional.of(Defaults.TENANT) }
    }

    override fun getAnnotationType(): Class<Tenant> {
        return Tenant::class.java
    }
}