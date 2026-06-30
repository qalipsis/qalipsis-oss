/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.report.meters

import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.core.head.communication.CampaignMeterFeedbackListener
import io.qalipsis.core.meters.CampaignMeterSnapshot
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import javax.sql.DataSource

internal abstract class AbstractCampaignMeterFeedbackListener : CampaignMeterFeedbackListener {

    protected abstract val dataSource: DataSource

    override suspend fun notify(feedback: CampaignMetersFeedback) {
        if (feedback.meters.isNotEmpty()) {
            tryAndLogOrNull(log) {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(INSERT_SQL).use { statement ->
                        feedback.meters.forEach { meter ->
                            bindCampaignMeterSnapshot(statement, meter)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
            }
        }
    }

    fun bindCampaignMeterSnapshot(statement: PreparedStatement, meter: CampaignMeterSnapshot) {
        var idx = 1
        statement.setString(idx++, meter.name)
        if (meter.tags != null) statement.setString(idx++, meter.tags) else statement.setNull(idx++, Types.VARCHAR)
        statement.setTimestamp(idx++, Timestamp.from(Instant.ofEpochMilli(meter.timestampEpochMs)))
        if (meter.tenant != null) statement.setString(idx++, meter.tenant) else statement.setNull(idx++, Types.VARCHAR)
        if (meter.campaign != null) statement.setString(idx++, meter.campaign) else statement.setNull(
            idx++,
            Types.VARCHAR
        )
        if (meter.scenario != null) statement.setString(idx++, meter.scenario) else statement.setNull(
            idx++,
            Types.VARCHAR
        )
        statement.setString(idx++, meter.type)
        if (meter.count != null) statement.setBigDecimal(idx++, BigDecimal(meter.count!!)) else statement.setNull(
            idx++,
            Types.DOUBLE
        )
        if (meter.value != null) statement.setBigDecimal(idx++, BigDecimal(meter.value!!)) else statement.setNull(
            idx++,
            Types.DOUBLE
        )
        if (meter.sum != null) statement.setBigDecimal(idx++, BigDecimal(meter.sum!!)) else statement.setNull(
            idx++,
            Types.DOUBLE
        )
        if (meter.mean != null) statement.setBigDecimal(idx++, BigDecimal(meter.mean!!)) else statement.setNull(
            idx++,
            Types.DOUBLE
        )
        if (meter.unit != null) statement.setString(idx++, meter.unit) else statement.setNull(idx++, Types.VARCHAR)
        if (meter.max != null) statement.setBigDecimal(idx++, BigDecimal(meter.max!!)) else statement.setNull(
            idx++,
            Types.DOUBLE
        )
        if (meter.other != null) statement.setString(idx, meter.other) else statement.setNull(idx, Types.VARCHAR)
    }


    private companion object {

        const val INSERT_SQL =
            "INSERT INTO campaign_meters (name, tags, timestamp, tenant, campaign, scenario, \"type\", \"count\", \"value\", \"sum\", mean, unit, \"max\", other)" +
                    " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

        val log = logger()
    }
}
