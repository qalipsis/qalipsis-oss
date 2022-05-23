package io.qalipsis.core.head.security

/**
 * Security permission granted to a [User] or [RoleName].
 */
internal typealias Permission = String

internal object Permissions {

    const val CREATE_CAMPAIGN = "create:campaign"
    const val READ_CAMPAIGN = "read:campaign"
    const val READ_SCENARIO = "read:scenario"

    val FOR_TESTER = setOf(
        CREATE_CAMPAIGN,
        READ_CAMPAIGN,
        READ_SCENARIO,
    )

    val FOR_REPORTER = setOf(
        READ_CAMPAIGN,
        READ_SCENARIO,
    )

    val FOR_TENANT_ADMINISTRATOR = emptySet<Permission>()

    val FOR_BILLING_ADMINISTRATOR = emptySet<Permission>()

    val ALL_PERMISSIONS = setOf(
        CREATE_CAMPAIGN,
        READ_CAMPAIGN,
        READ_SCENARIO,
    )

}