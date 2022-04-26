package io.qalipsis.core.head.admin

import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import jakarta.inject.Singleton
import java.util.UUID


/**
 * @author Sandro Mamukelashvili
 */

@Singleton
internal class TenantManagementImpl(
    private val tenantRepository: TenantRepository
): TenantManagement {

    override suspend fun saveTenant(saveTenantDto: SaveTenantDto): SaveTenantResponse {

        val tenant = tenantRepository.save(TenantEntity(
            displayName = saveTenantDto.displayName,
            reference = UUID.randomUUID().toString()
        )
        )

        return SaveTenantResponse(
            displayName = tenant.displayName,
            reference = tenant.reference,
            version = null
        )

    }
}