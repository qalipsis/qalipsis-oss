package io.qalipsis.core.head.web.handler

import io.micronaut.context.annotation.Requires
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

/**
 * Exception handler for the Micronaut data exception, when a query returns no object.
 * We only return the information that the record could not be found.
 *
 * If a smarter handling is to be done, the service in charge of the call should manage it.
 *
 * @author Eric Jessé
 */
@Produces
@Singleton
@Requires(classes = [EmptyResultException::class, ExceptionHandler::class])
internal class EmptyResultExceptionHandler : ExceptionHandler<EmptyResultException, MutableHttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: EmptyResultException): MutableHttpResponse<*> {
        return HttpResponse.status<Any>(HttpStatus.NOT_FOUND)
    }
}