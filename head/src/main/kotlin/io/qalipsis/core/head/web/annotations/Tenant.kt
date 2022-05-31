package io.qalipsis.core.head.web.annotations

import io.micronaut.core.bind.annotation.Bindable

/**
 * Annotation of REST argument to inject the tenant reference of the context of the request.
 *
 * @author Palina Bril
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
@Bindable
annotation class Tenant