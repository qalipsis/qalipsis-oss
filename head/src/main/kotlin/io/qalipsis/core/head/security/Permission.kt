package io.qalipsis.core.head.security

/**
 * Security permission granted to a [User] or [RoleName].
 */
internal typealias Permission = String

internal object Permissions {

    const val WRITE_CAMPAIGN = "write:campaign"
    const val READ_CAMPAIGN = "read:campaign"
    const val READ_SCENARIO = "read:scenario"
    const val WRITE_DATA_SERIES = "write:series"
    const val READ_DATA_SERIES = "read:series"
    const val READ_REPORT = "read:report"
    const val WRITE_REPORT = "write:report"
    const val READ_TIME_SERIES = "read:time-series"

    val FOR_TESTER = setOf(
        WRITE_CAMPAIGN,
        READ_CAMPAIGN,
        READ_SCENARIO,
        WRITE_DATA_SERIES,
        READ_TIME_SERIES,
    )

    val FOR_REPORTER = setOf(
        READ_CAMPAIGN,
        READ_SCENARIO,
        READ_DATA_SERIES,
        WRITE_DATA_SERIES,
        READ_REPORT,
        WRITE_REPORT,
        WRITE_DATA_SERIES,
        READ_TIME_SERIES,
    )

    val FOR_TENANT_ADMINISTRATOR = emptySet<Permission>()

    val FOR_BILLING_ADMINISTRATOR = emptySet<Permission>()

    val ALL_PERMISSIONS = setOf(
        WRITE_CAMPAIGN,
        READ_CAMPAIGN,
        READ_SCENARIO,
        WRITE_DATA_SERIES,
        READ_DATA_SERIES,
        READ_REPORT,
        WRITE_REPORT,
        READ_DATA_SERIES,
        READ_TIME_SERIES,
    )

}