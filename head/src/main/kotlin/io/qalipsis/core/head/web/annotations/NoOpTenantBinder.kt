package io.qalipsis.core.head.web.annotations

import io.micronaut.context.annotation.Infrastructure
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import io.qalipsis.core.configuration.ExecutionEnvironments
import java.util.Optional

/**
 * Binder to inject the default tenant reference when no security is active.
 */
@Infrastructure
@Requirements(Requires(env = [ExecutionEnvironments.HEAD]))
internal class NoOpTenantBinder : AnnotatedRequestArgumentBinder<Tenant, String> {

    override fun bind(
        context: ArgumentConversionContext<String>,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<String> {
        return ArgumentBinder.BindingResult<String> { Optional.of("_qalipsis_") }
    }

    override fun getAnnotationType(): Class<Tenant> {
        return Tenant::class.java
    }
}