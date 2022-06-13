package io.qalipsis.core.head.security

import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreationRequest

/**
 * Interface to manage operations on tenants.
 *
 * @author Sandro Mamukelashvili
 */
internal interface TenantManagement {

    /**
     * Creates a brand new tenant.
     */
    suspend fun create(tenantCreationRequest: TenantCreationRequest): Tenant

    /**
     * Lists all the tenants with the provided references.
     */
    suspend fun findAll(references: Collection<String>): Collection<Tenant>
}