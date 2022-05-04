package io.qalipsis.core.head.web

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.qalipsis.core.head.admin.TenantManagement
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreation
import javax.validation.Valid

@Validated
@Controller("/api/admin/tenants")
internal class TenantController(
    private val tenantManagement: TenantManagement
) {

    @Post
    suspend fun createTenants(@Body @Valid tenantCreation: TenantCreation): HttpResponse<Tenant> {
        return HttpResponse.ok(tenantManagement.saveTenant(tenantCreation))
    }
}