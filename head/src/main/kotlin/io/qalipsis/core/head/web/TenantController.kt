package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.validation.Validated
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreationRequest
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.security.TenantManagement
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.validation.Valid

/**
 * Controller for REST calls related to tenant operations.
 *
 * @author Sandro Mamukelashvili
 */
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD]), Requires(beans = [TenantManagement::class])
)
@Controller("/tenants")
@Validated
@Version("1.0")
internal class TenantController(
    private val tenantManagement: TenantManagement
) {

    @Post
    @Operation(
        summary = "Create a new tenant",
        description = "Create a new tenant with the provided details",
        responses = [
            ApiResponse(responseCode = "200", description = "Details of the successfully created tenant"),
            ApiResponse(responseCode = "400", description = "Invalid request supplied"),
            ApiResponse(responseCode = "401", description = "Operation not allowed"),
        ],
        security = [SecurityRequirement(name = "JWT")]
    )
    @Secured(Permissions.WRITE_TENANT)
    suspend fun createTenants(@Body @Valid tenantCreationRequest: TenantCreationRequest): HttpResponse<Tenant> {
        return HttpResponse.ok(tenantManagement.create(tenantCreationRequest))
    }
}