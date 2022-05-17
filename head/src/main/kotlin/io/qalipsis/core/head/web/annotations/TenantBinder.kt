package io.qalipsis.core.head.web.annotations

import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Binder to inject the tenant reference of the context of the request.
 */
@Singleton
internal class TenantBinder : AnnotatedRequestArgumentBinder<Tenant, String> {

    override fun bind(
        context: ArgumentConversionContext<String>,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<String> {
        val headers: HttpHeaders = source.headers
        return ArgumentBinder.BindingResult<String> { Optional.ofNullable(headers.get("X-Tenant")) }
    }

    override fun getAnnotationType(): Class<Tenant> {
        return Tenant::class.java
    }
}