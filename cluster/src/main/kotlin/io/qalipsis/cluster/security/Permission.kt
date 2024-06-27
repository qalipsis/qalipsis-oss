/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.cluster.security

/**
 * Security permission granted to a [User] or [RoleName].
 */
typealias Permission = String

object Permissions {

    const val WRITE_CAMPAIGN = "write:campaign"
    const val READ_CAMPAIGN = "read:campaign"
    const val READ_SCENARIO = "read:scenario"
    const val WRITE_DATA_SERIES = "write:series"
    const val READ_DATA_SERIES = "read:series"
    const val READ_REPORT = "read:report"
    const val WRITE_REPORT = "write:report"
    const val READ_TIME_SERIES = "read:time-series"
    const val WRITE_DATA_SERIES_PREPARED_QUERIES = "write:series-prepared-queries"

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