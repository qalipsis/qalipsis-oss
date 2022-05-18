package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info

@OpenAPIDefinition(
    info = Info(
        title = "QALIPSIS",
        version = "1.0",
        description = "QALIPSIS Campaign API"
    )
)
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Controller("/")
internal class HomeController {

    @Hidden
    @Get(produces = [MediaType.TEXT_PLAIN])
    fun plainHome() = "Welcome to Qalipsis"

}