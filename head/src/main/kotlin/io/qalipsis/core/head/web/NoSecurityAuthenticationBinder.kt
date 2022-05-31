package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Infrastructure
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.ServerAuthentication
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.security.RoleName
import java.util.Optional

/**
 * Binder to inject the default user when no security is active.
 */
@Infrastructure
@Requirements(Requires(env = [ExecutionEnvironments.HEAD]))
internal class NoSecurityAuthenticationBinder : TypedRequestArgumentBinder<Authentication> {
    override fun bind(
        context: ArgumentConversionContext<Authentication>,
        source: HttpRequest<*>
    ): ArgumentBinder.BindingResult<Authentication> {
        return ArgumentBinder.BindingResult {
            Optional.of(
                ServerAuthentication(
                    "_qalipsis_",
                    RoleName.values().asSequence().flatMap { it.permissions }.toSet(),
                    emptyMap()
                )
            )
        }
    }

    override fun argumentType(): Argument<Authentication> {
        return Argument.of(Authentication::class.java)
    }

}