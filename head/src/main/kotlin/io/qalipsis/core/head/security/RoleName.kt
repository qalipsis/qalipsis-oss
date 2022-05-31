package io.qalipsis.core.head.security

/**
 * Role of a user into QALIPSIS.
 *
 * @property publicName name publicly available.
 * @property permissions permissions granted to the users having the role.
 *
 */
internal enum class RoleName(
    val publicName: String,
    val permissions: Collection<Permission> = emptySet()
) {
    /**
     * Role of user having the administrator rights.
     */
    SUPER_ADMINISTRATOR("super-admin", Permissions.ALL_PERMISSIONS),

    /**
     * Role of user having the rights to edit the billing details of the tenant.
     */
    BILLING_ADMINISTRATOR("billing-admin", Permissions.FOR_BILLING_ADMINISTRATOR),

    /**
     * Role of user having the rights to edit the tenant details and its users.
     */
    TENANT_ADMINISTRATOR("tenant-admin", Permissions.FOR_TENANT_ADMINISTRATOR),

    /**
     * Role of user having the rights to start and edit campaigns.
     */
    TESTER("tester", Permissions.FOR_TESTER),

    /**
     * Role of user having the rights to vizualize the results of campaigns.
     */
    REPORTER("reporter", Permissions.FOR_REPORTER);

    companion object {

        private val ROLES_BY_NAMES = values().associateBy { it.publicName }

        fun fromPublicName(publicName: String) = ROLES_BY_NAMES[publicName]!!

    }
}