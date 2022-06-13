package io.qalipsis.core.head.security

/**
 * Security permission granted to a [User] or [RoleName].
 */
internal typealias Permission = String

internal object Permissions {

    /**
     * Virtual permissions assigned to all the authenticated users.
     * It should not be added to [ALL_PERMISSIONS].
     */
    const val AUTHENTICATED = "authenticated"

    const val CREATE_CAMPAIGN = "create:campaign"
    const val READ_CAMPAIGN = "read:campaign"
    const val ABORT_CAMPAIGN = "abort:campaign"
    const val READ_SCENARIO = "read:scenario"
    const val WRITE_USER = "write:user"
    const val READ_USER = "read:user"
    const val DELETE_USER = "delete:user"
    const val WRITE_TENANT = "write:tenant"

    val FOR_USER = emptySet<Permission>()

    val FOR_TESTER = setOf(
        CREATE_CAMPAIGN,
        ABORT_CAMPAIGN,
        READ_CAMPAIGN,
        READ_SCENARIO,
    )

    val FOR_REPORTER = setOf(
        READ_CAMPAIGN,
        READ_SCENARIO,
    )

    val FOR_TENANT_ADMINISTRATOR = setOf(
        WRITE_USER,
        DELETE_USER,
        READ_USER
    )

    val FOR_BILLING_ADMINISTRATOR = emptySet<Permission>()

    val ALL_PERMISSIONS = setOf(
        CREATE_CAMPAIGN,
        ABORT_CAMPAIGN,
        READ_CAMPAIGN,
        READ_SCENARIO,
        WRITE_USER,
        READ_USER,
        DELETE_USER,
        WRITE_TENANT
    )

}