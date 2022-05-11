package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.validation.Validated
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.web.requestAnnotation.Tenant
import javax.annotation.Nullable

@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Validated
@Controller("/scenarios")
internal class ScenarioController(
    private val factoryService: FactoryService
) {

    @Get
    suspend fun listScenarios(
        @Tenant tenant: String,
        @Nullable @QueryValue sort: String
    ): HttpResponse<List<ScenarioSummary>> {
        return HttpResponse.ok(factoryService.getAllActiveScenarios(tenant, sort).toList())
    }
}