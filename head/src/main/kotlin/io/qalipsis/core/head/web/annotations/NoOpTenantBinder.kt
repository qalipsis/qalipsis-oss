package io.qalipsis.core.head.web.annotations

import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import io.qalipsis.core.head.jdbc.entity.Defaults
import jakarta.inject.Singleton
import java.util.Optional

/**
 * Binder to inject the default tenant reference when no security is active.
 */
@Singleton
@Requires(property = "identity.bind-tenant", defaultValue = StringUtils.FALSE, notEquals = StringUtils.TRUE)
internal class NoOpTenantBinder : AnnotatedRequestArgumentBinder<Tenant, String> {

    override fun bind(
        context: ArgumentConversionContext<String>,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<String> {
        return ArgumentBinder.BindingResult<String> { Optional.of(Defaults.TENANT) }
    }

    override fun getAnnotationType(): Class<Tenant> {
        return Tenant::class.java
    }
}