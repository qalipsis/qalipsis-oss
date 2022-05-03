package io.qalipsis.core.head.security.entity

/**
 * Role details
 *
 * @author Palina Bril
 */
open class QalipsisRole(
    var name: String,
    var description: String
) {
    var permissions: List<QalipsisPermission> = mutableListOf()
}

internal class SuperAdministratorRole(tenantName: String) : QalipsisRole(
    name = "$tenantName:super-admin",
    description = "Include all possible roles"
)

internal class BillingAdminitratorRole(tenantName: String) : QalipsisRole(
    name = "$tenantName:billing-admin",
    description = "Only relevant for the cloud solution to maintain their payment modes and subscriptions in the webshop"
)

internal class TenantAdministratorRole(tenantName: String) : QalipsisRole(
    name = "$tenantName:tenant-admin",
    description = "Can manage users, tenant naming"
)

internal class TesterRole(tenantName: String) : QalipsisRole(
    name = "$tenantName:tester",
    description = "Can configure a new campaign, see the results and reports"
)

internal class ReporterRole(tenantName: String) : QalipsisRole(
    name = "$tenantName:reporter",
    description = "Can only see the results and reports"
)