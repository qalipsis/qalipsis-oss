package io.qalipsis.core.head.admin

import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreation
import jakarta.inject.Singleton
import java.util.UUID


/**
 * @author Sandro Mamukelashvili
 */

@Singleton
internal class TenantManagementImpl(
    private val tenantRepository: TenantRepository
) : TenantManagement {

    override suspend fun saveTenant(tenantCreation: TenantCreation): Tenant {
        val tenant = tenantRepository.save(
            TenantEntity(
                displayName = tenantCreation.displayName,
                reference = UUID.randomUUID().toString()
            )
        )

        return Tenant(
            displayName = tenant.displayName,
            reference = tenant.reference,
            version = tenant.version
        )

    }
}