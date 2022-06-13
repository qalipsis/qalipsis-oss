package io.qalipsis.core.head.security

import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreationRequest
import jakarta.inject.Singleton


/**
 * Default implementation of [TenantManagement] interface.
 * TenantRepository and IdGenerator beans should be injected.
 *
 * @author Sandro Mamukelashvili
 */
@Singleton
internal class TenantManagementImpl(
    private val idGenerator: IdGenerator,
    private val tenantRepository: TenantRepository
) : TenantManagement {

    override suspend fun create(tenantCreationRequest: TenantCreationRequest): Tenant {
        val tenant = tenantRepository.save(
            TenantEntity(
                displayName = tenantCreationRequest.displayName,
                reference = tenantCreationRequest.reference?.takeUnless(String::isNullOrBlank) ?: idGenerator.short()
            )
        )

        return Tenant(
            displayName = tenant.displayName,
            reference = tenant.reference,
            version = tenant.version
        )

    }

    override suspend fun findAll(references: Collection<String>): Collection<Tenant> {
        return tenantRepository.findByReferenceIn(references).map {
            Tenant(
                displayName = it.displayName,
                reference = it.reference,
                version = it.version
            )
        }
    }
}