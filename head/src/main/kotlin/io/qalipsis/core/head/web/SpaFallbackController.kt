/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.swagger.v3.oas.annotations.Hidden
import jakarta.annotation.PostConstruct
import java.nio.charset.StandardCharsets

/**
 * Serves the Nuxt SPA fallback shell (`public/200.html`) for browser requests that
 * target application routes without a physical static file.
 *
 * Two cases are covered:
 *  - Section root paths that resolve to a classpath directory entry (e.g. `/campaigns`,
 *    `/reports`, `/series`) — the static-resources handler returns an empty 200, so an
 *    explicit `@Get` on each known section serves the SPA shell instead.
 *  - Any other unmatched route — the global `NOT_FOUND` handler serves the SPA shell
 *    for HTML clients and falls back to HAL JSON for API/JSON clients.
 */
@Hidden
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(env = [ExecutionEnvironments.GUI]),
    Requires(property = "spa.fallback.enabled", notEquals = "false"),
)
@Controller
internal class SpaFallbackController(
    @Value("\${server.api-root:/api}") private val apiRoot: String,
) {

    private var spaFallbackHtml: String? = null

    @PostConstruct
    fun loadFallback() {
        javaClass.classLoader.getResourceAsStream(SPA_FALLBACK_RESOURCE)?.use {
            spaFallbackHtml = it.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    @Secured(SecurityRule.IS_ANONYMOUS)
    @Get(uris = ["/campaigns", "/reports", "/series"], produces = [MediaType.TEXT_HTML])
    fun serveSpaShell(): MutableHttpResponse<*> {
        val html = spaFallbackHtml ?: return HttpResponse.notFound<Any>()
        return HttpResponse.ok(html).contentType(MediaType.TEXT_HTML_TYPE)
    }

    @Secured(SecurityRule.IS_ANONYMOUS)
    @Error(global = true, status = HttpStatus.NOT_FOUND)
    fun handleNotFound(request: HttpRequest<*>): MutableHttpResponse<*> {
        val html = spaFallbackHtml
        val path = request.path
        val isApiPath = path == apiRoot || path.startsWith("$apiRoot/")
        val acceptsHtml = request.headers.accept().any {
            it.type == MediaType.TEXT_HTML_TYPE.type && it.subtype == MediaType.TEXT_HTML_TYPE.subtype
        }
        return if (!isApiPath && acceptsHtml && html != null) {
            HttpResponse.ok(html).contentType(MediaType.TEXT_HTML_TYPE)
        } else {
            val error = JsonError("Page Not Found").link(Link.SELF, Link.of(request.uri))
            HttpResponse.notFound(error)
        }
    }

    companion object {
        private const val SPA_FALLBACK_RESOURCE = "public/200.html"
    }
}
