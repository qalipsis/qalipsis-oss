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
internal class IllegalArgumentExceptionHandler : ExceptionHandler<IllegalArgumentException, MutableHttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: IllegalArgumentException): MutableHttpResponse<*> {
        return HttpResponse.status<ErrorResponse>(HttpStatus.BAD_REQUEST).body(ErrorResponse(exception.message!!))
    }
}