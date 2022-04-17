package io.qalipsis.core.head.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

@Controller("api/admin")
class AdminController(
    private val tenantManagement: TenantManagement
) {



    @Post("tenant")
    suspend fun createTenant(@Body saveTenantDto: SaveTenantDto): HttpResponse<Any> {
        return HttpResponse.ok(tenantManagement.saveTenant(saveTenantDto))
    }
}