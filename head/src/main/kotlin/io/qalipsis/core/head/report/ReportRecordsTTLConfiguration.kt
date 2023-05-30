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

package io.qalipsis.core.head.report

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import io.qalipsis.api.constraints.PositiveDuration
import java.time.Duration

/**
 * Configuration for cleaning up report records.
 *
 * @author Francisca Eze
 */
@ConfigurationProperties("report.records")
internal interface ReportRecordsTTLConfiguration {

    /**
     * Duration after which old report file records are deleted.
     */
    @get:PositiveDuration
    @get:Bindable(defaultValue = "P4D")
    val fileTimeToLive: Duration?

    /**
     * Duration after which old report task records are deleted.
     */
    @get:PositiveDuration
    @get:Bindable(defaultValue = "P30D")
    val taskTimeToLive: Duration?

    /**
     * Valid cron expression to trigger report records cleanup.
     */
    @get:Bindable(defaultValue = "0 0 0 1/1 * ?")
    val cron: String?
}