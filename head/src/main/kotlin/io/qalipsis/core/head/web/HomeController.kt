package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info

@OpenAPIDefinition(
    info = Info(
        title = "QALIPSIS",
        version = "1.0",
        description = "QALIPSIS Management and Campaign API"
    )
)
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Controller("/")
internal class HomeController {

    @Hidden
    @Get(produces = [MediaType.TEXT_PLAIN])
    @Secured(SecurityRule.IS_ANONYMOUS)
    fun plainHome() = "Welcome to Qalipsis"

}