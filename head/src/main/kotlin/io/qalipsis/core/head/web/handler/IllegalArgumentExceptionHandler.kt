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

package io.qalipsis.core.head.web.handler

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/**
 * Exception handler for the Micronaut data exception, when a required argument is not set.
 *
 * @author Eric Jessé
 */
@Produces
@Singleton
@Requires(classes = [IllegalArgumentException::class, ExceptionHandler::class])
class IllegalArgumentExceptionHandler : ExceptionHandler<IllegalArgumentException, MutableHttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: IllegalArgumentException): MutableHttpResponse<*> {
        return HttpResponse.status<ErrorResponse>(HttpStatus.BAD_REQUEST).body(ErrorResponse(exception.message!!))
    }
}