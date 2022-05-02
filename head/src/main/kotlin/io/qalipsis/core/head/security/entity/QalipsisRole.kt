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

internal class SuperAdministratorRole : QalipsisRole(
    name = "super-admin",
    description = "Include all possible roles"
)

internal class BillingAdminitratorRole : QalipsisRole(
    name = "billing-admin",
    description = "Only relevant for the cloud solution to maintain their payment modes and subscriptions in the webshop"
)

internal class TenantAdministratorRole : QalipsisRole(
    name = "tenant-admin",
    description = "Can manage users, tenant naming"
)

internal class TesterRole : QalipsisRole(
    name = "tester",
    description = "Can configure a new campaign, see the results and reports"
)

internal class ReporterRole : QalipsisRole(
    name = "reporter",
    description = "Can only see the results and reports"
)