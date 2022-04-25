package io.qalipsis.core.head.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import javax.validation.Valid

@Validated
@Controller("/api/admin")
class AdminController(
    private val tenantManagement: TenantManagement
) {
    @Post("tenants")
    suspend fun createTenants(@Body @Valid saveTenantDto: SaveTenantDto): HttpResponse<SaveTenantResponse> {
        return HttpResponse.ok(tenantManagement.saveTenant(saveTenantDto))
    }
}