package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.web.model.Zone
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement


/**
 * Zone management API.
 *
 * @author Palina Bril
 */
@Validated
@Version("1.0")
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
@Controller("/zones")
internal class ZoneController(val headConfiguration: HeadConfiguration) {

    @Get
    @Operation(
        summary = "List of zones",
        description = "Return selected zones to execute scenarios,",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of zones for scenarios"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [
            SecurityRequirement(name = "JWT")
        ]
    )
    suspend fun listZones(): HttpResponse<List<Zone>> {
        return HttpResponse.ok(headConfiguration.zones.toList())
    }
}