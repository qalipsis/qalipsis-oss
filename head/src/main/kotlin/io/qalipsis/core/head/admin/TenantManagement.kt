package io.qalipsis.core.head.admin

interface TenantManagement {
    suspend fun saveTenant(saveTenantDto: SaveTenantDto): SaveTenantResponse
}