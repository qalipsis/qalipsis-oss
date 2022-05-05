package io.qalipsis.core.head.security.entity

/**
 * Role details
 *
 * @author Palina Bril
 */
internal open class QalipsisRole(
    var name: String,
    var description: String
) {
    var permissions: List<QalipsisPermission> = mutableListOf()
}

internal class SuperAdministratorRole(tenant: String) : QalipsisRole(
    name = "$tenant:super-admin",
    description = "Include all possible roles"
)

internal class BillingAdminitratorRole(tenant: String) : QalipsisRole(
    name = "$tenant:billing-admin",
    description = "Only relevant for the cloud solution to maintain their payment modes and subscriptions in the webshop"
)

internal class TenantAdministratorRole(tenant: String) : QalipsisRole(
    name = "$tenant:tenant-admin",
    description = "Can manage users, tenant naming"
)

internal class TesterRole(tenant: String) : QalipsisRole(
    name = "$tenant:tester",
    description = "Can configure a new campaign, see the results and reports"
)

internal class ReporterRole(tenant: String) : QalipsisRole(
    name = "$tenant:reporter",
    description = "Can only see the results and reports"
)