/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.api.report

/**
 * Severity of a [ReportMessage].
 *
 * @author Eric Jessé
 */
enum class ReportMessageSeverity {
    /**
     * Severity for messages that have no impact on the final result and are just for user information.
     */
    INFO,

    /**
     * Severity for issues that have no impact on the final result but could potentially have negative side effect.
     */
    WARN,

    /**
     * Severity for issues that will let the campaign continue until the end but will make the campaign fail.
     */
    ERROR,

    /**
     * Severity for issues that will immediately abort the campaign.
     */
    ABORT

}
