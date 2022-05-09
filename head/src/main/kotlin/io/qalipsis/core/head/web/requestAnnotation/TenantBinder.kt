package io.qalipsis.core.head.web.requestAnnotation

import io.micronaut.core.bind.ArgumentBinder.BindingResult
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import jakarta.inject.Singleton
import java.util.Optional

@Singleton
class TenantBinder : AnnotatedRequestArgumentBinder<Tenant, String> {
    override fun bind(
        context: ArgumentConversionContext<String>?, source: HttpRequest<*>?
    ): BindingResult<String> {

        val headers: HttpHeaders = source!!.headers
        return BindingResult<String> {
            Optional.of(headers.get("X-Tenant"))
        }
    }

    override fun getAnnotationType(): Class<Tenant> {
        return Tenant::class.java
    }
}