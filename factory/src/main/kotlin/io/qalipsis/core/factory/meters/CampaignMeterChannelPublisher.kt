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

package io.qalipsis.core.factory.meters

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.config.MetersConfig.EXPORT_CONFIGURATION
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.DefaultMeasurementPublisherFactory
import io.qalipsis.api.meters.MeasurementPublisher
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.core.configuration.ExecutionEnvironments.FACTORY
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import jakarta.inject.Singleton

@Singleton
@Requirements(
    Requires(env = [FACTORY, STANDALONE]),
    Requires(property = "${EXPORT_CONFIGURATION}.default.enabled", notEquals = "false")
)
internal class CampaignMeterChannelPublisher(
    private val factoryChannel: FactoryChannel,
    private val objectMapper: ObjectMapper
) : MeasurementPublisher, DefaultMeasurementPublisherFactory {

    override fun getPublisher(): MeasurementPublisher = this

    override suspend fun publish(meters: Collection<MeterSnapshot>) {
        val campaignMeters = meters.filter {
            it.meterId.tags["scope"] == "campaign" || it.meterId.meterName == "running-minions"
        }
        if (campaignMeters.isEmpty()) return
        val converted = campaignMeters.mapNotNull { snapshot ->
            tryAndLogOrNull(log) { CampaignMeterConverter.convert(snapshot, objectMapper) }
        }
        if (converted.isNotEmpty()) {
            tryAndLogOrNull(log) {
                factoryChannel.publishMeterFeedback(CampaignMetersFeedback(meters = converted))
            }
        }
    }

    private companion object {
        val log = logger()
    }
}
