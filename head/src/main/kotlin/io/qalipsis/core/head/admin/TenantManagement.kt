package io.qalipsis.core.head.admin

import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreation

internal interface TenantManagement {

    suspend fun saveTenant(tenantCreation: TenantCreation): Tenant

}