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
    val permissions: Set<Permission> = emptySet()
) {
    SUPER_ADMINISTRATOR("super-admin", setOf(ALL_PERMISSION)),
    BILLING_ADMINISTRATOR("billing-admin"),
    TENANT_ADMINISTRATOR("tenant-admin"),
    TESTER("tester"),
    REPORTER("reporter");

    /**
     * Roles that a user with this role can assign to another user (by invitation or administration).
     */
    val assignableRoles: Collection<RoleName>
        get() = when (this) {
            SUPER_ADMINISTRATOR -> setOf(
                SUPER_ADMINISTRATOR,
                BILLING_ADMINISTRATOR,
                TENANT_ADMINISTRATOR,
                TESTER,
                REPORTER
            )
            BILLING_ADMINISTRATOR -> setOf(BILLING_ADMINISTRATOR)
            TENANT_ADMINISTRATOR -> setOf(TENANT_ADMINISTRATOR, TESTER, REPORTER)
            TESTER -> setOf(TESTER, REPORTER)
            REPORTER -> setOf(REPORTER)
        }

    companion object {

        private val ROLES_BY_NAMES = values().associateBy { it.publicName }

        fun fromPublicName(publicName: String) = ROLES_BY_NAMES[publicName]!!

    }
}