package io.qalipsis.core.head.web.handler

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.validation.exceptions.ConstraintExceptionHandler
import jakarta.inject.Singleton
import javax.validation.ConstraintViolation
import javax.validation.ConstraintViolationException
import javax.validation.ElementKind
import javax.validation.Path

/**
 * Exception handler for the Micronaut data exception, when a required argument is not set.
 *
 * @author Eric Jessé
 */
@Produces
@Singleton
@Requires(classes = [IllegalArgumentException::class, ExceptionHandler::class])
@Replaces(ConstraintExceptionHandler::class)
internal class ConstraintViolationExceptionHandler :
    ExceptionHandler<ConstraintViolationException, HttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: ConstraintViolationException): HttpResponse<*> {
        val messages = exception.constraintViolations.map {
            ValidationErrorResponse.ValidationError(buildProperty(it), it.message)
        }
        return HttpResponse.status<ErrorResponse>(HttpStatus.BAD_REQUEST).body(ValidationErrorResponse(messages))
    }

    /**
     * Builds a message based on the provided violation.
     *
     * @param violation The constraint violation
     * @return The violation message
     */
    protected fun buildProperty(violation: ConstraintViolation<*>): String {
        val propertyPath = violation.propertyPath
        val i: Iterator<Path.Node> = propertyPath.iterator()
        val property = StringBuilder()
        while (i.hasNext()) {
            val node = i.next()
            if (node.kind == ElementKind.METHOD || node.kind == ElementKind.CONSTRUCTOR) {
                // Skip the part of the method.
                continue
            }
            property.append(node.name)
            if (node.index != null) {
                property.append(String.format("[%d]", node.index))
            }
            if (i.hasNext()) {
                property.append('.')
            }
        }
        return property.toString()
    }
}