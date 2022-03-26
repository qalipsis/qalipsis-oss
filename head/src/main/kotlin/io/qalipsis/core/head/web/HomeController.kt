package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.qalipsis.core.configuration.ExecutionEnvironments

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Controller("/")
internal class HomeController {

    @Get(produces = [MediaType.TEXT_PLAIN])
    fun plainHome() = "Welcome to Qalipsis"

}