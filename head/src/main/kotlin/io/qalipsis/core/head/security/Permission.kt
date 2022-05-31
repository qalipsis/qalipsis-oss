package io.qalipsis.core.head.security

/**
 * Security permission granted to a [User] or [RoleName].
 */
internal typealias Permission = String

internal object Permissions {

    const val CREATE_CAMPAIGN = "create:campaign"
    const val READ_CAMPAIGN = "read:campaign"
    const val WRITE_USER = "write:user"
    const val READ_USER = "read:user"
    const val DELETE_USER = "delete:user"

    val FOR_USER = emptySet<Permission>()

    val FOR_TESTER = setOf(
        CREATE_CAMPAIGN,
        READ_CAMPAIGN,
    )

    val FOR_REPORTER = setOf(
        READ_CAMPAIGN,
    )

    val FOR_TENANT_ADMINISTRATOR = setOf(
        WRITE_USER,
        DELETE_USER,
        READ_USER
    )

    val FOR_BILLING_ADMINISTRATOR = emptySet<Permission>()

    val ALL_PERMISSIONS = setOf(
        CREATE_CAMPAIGN,
        READ_CAMPAIGN,
        WRITE_USER
    )

}