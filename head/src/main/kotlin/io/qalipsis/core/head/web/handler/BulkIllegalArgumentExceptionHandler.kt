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
 * [BulkIllegalArgumentException] handler.
 *
 * @author Joël Valère
 */
@Produces
@Singleton
@Requires(classes = [BulkIllegalArgumentException::class, ExceptionHandler::class])
class BulkIllegalArgumentExceptionHandler :
    ExceptionHandler<BulkIllegalArgumentException, MutableHttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: BulkIllegalArgumentException): MutableHttpResponse<*> {
        return HttpResponse.status<ErrorResponse>(HttpStatus.BAD_REQUEST).body(ErrorResponse(exception.messages))
    }
}