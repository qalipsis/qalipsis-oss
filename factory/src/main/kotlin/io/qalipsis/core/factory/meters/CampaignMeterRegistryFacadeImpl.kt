/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Value
import io.qalipsis.api.Executors
import io.qalipsis.api.config.MetersConfig
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.MeasurementPublisher
import io.qalipsis.api.meters.MeasurementPublisherFactory
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Implementation of meter registry.
 */
@Singleton
internal class CampaignMeterRegistryFacadeImpl(
    private val publisherFactories: Collection<MeasurementPublisherFactory>,
    private val meterRegistry: MeterRegistry,
    factoryConfiguration: FactoryConfiguration,
    private val measurementConfiguration: MeasurementConfiguration,
    @Value("\${${MetersConfig.EXPORT_CONFIGURATION}.campaign-step:PT5S}") private val step: Duration,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
) : CampaignMeterRegistry, CampaignLifeCycleAware {

    @KTestable
    private val additionalTags = mutableMapOf<String, String>()

    @KTestable
    private var currentCampaignKey: CampaignKey? = null

    @KTestable
    private lateinit var ticker: ReceiveChannel<*>

    @KTestable
    private var publishers = listOf<MeasurementPublisher>()

    init {
        additionalTags.putAll(factoryConfiguration.tags)
        if (publisherFactories.isNotEmpty()) {
            // Additional tags to force to all the created meters.
            additionalTags.putAll(factoryConfiguration.tags)
            additionalTags[TENANT_KEY] = factoryConfiguration.tenant
            factoryConfiguration.zone?.takeIf { it.isNotBlank() }?.let { zone ->
                additionalTags["zone"] = zone
            }
        }
    }

    override suspend fun init(campaign: Campaign) {
        currentCampaignKey = campaign.campaignKey
        publishers = publisherFactories.map { it.getPublisher() }
        publishers.forEach { it.init() }
        startPublishingJob()
    }

    @KTestable
    private suspend fun startPublishingJob() {
        logger.debug { "Starting publishing job" }
        val stepMillis = step.toMillis()
        ticker = ticker(stepMillis, stepMillis, mode = TickerMode.FIXED_PERIOD)
        coroutineScope.launch {
            for (event in ticker) {
                try {
                    val snapshots = meterRegistry.snapshots(Instant.now().truncatedTo(ChronoUnit.SECONDS))
                    publishSnapshots(snapshots)
                } catch (e: Exception) {
                    logger.error(e) { "An error occurred while publishing the meters: ${e.message}" }
                }
            }
        }
    }

    override suspend fun close(campaign: Campaign) {
        logger.debug { "Closing the meter registry attached to the campaign $currentCampaignKey" }
        ticker.cancel()
        logger.debug { "Extracting the long-run snapshots for the campaign $currentCampaignKey" }
        val snapshots = meterRegistry.summarize(Instant.now().truncatedTo(ChronoUnit.SECONDS))
        logger.debug { "Publishing the ${snapshots.size} long-run snapshots for the campaign $currentCampaignKey" }
        publishSnapshots(snapshots).joinAll()
        logger.debug { "Shutting down the publication of meters for the campaign $currentCampaignKey" }
        publishers.forEach { it.stop() }
        logger.debug { "The publication of meters has been shut down for the campaign $currentCampaignKey" }
        currentCampaignKey = null
    }

    @KTestable
    private fun publishSnapshots(snapshots: Collection<MeterSnapshot>): Collection<Job> {
        return if (snapshots.isNotEmpty()) {
            publishers.map { publisher ->
                logger.trace { "Publishing ${snapshots.size} snapshots with publisher $publisher." }
                coroutineScope.launch {
                    try {
                        publisher.publish(snapshots)
                    } catch (e: Exception) {
                        logger.error(e) { "Publishing ${snapshots.size} snapshots failed for the publisher ${publisher::class}: ${e.message}" }
                    }
                }
            }
        } else {
            emptyList()
        }
    }

    override fun counter(name: String, vararg tags: String): Counter {
        return counter(asId(name, tagsAsMap(tags), MeterType.COUNTER))
    }

    override fun counter(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
    ): Counter {
        return counter(
            Meter.Id(
                meterName = name,
                tags = tags + additionalTags + mapOf(
                    CAMPAIGN_KEY to currentCampaignKey!!,
                    SCENARIO_NAME to scenarioName,
                    STEP_NAME to stepName
                ),
                type = MeterType.COUNTER
            )
        )
    }

    private fun counter(meterId: Meter.Id): Counter {
        return meterRegistry.counter(meterId)
    }


    override fun gauge(name: String, vararg tags: String): Gauge {
        return gauge(asId(name, tagsAsMap(tags), MeterType.GAUGE))
    }

    override fun rate(scenarioName: ScenarioName, stepName: StepName, name: String, tags: Map<String, String>): Rate {
        return rate(
            Meter.Id(
                meterName = name,
                tags = tags + additionalTags + mapOf(
                    CAMPAIGN_KEY to currentCampaignKey!!,
                    SCENARIO_NAME to scenarioName,
                    STEP_NAME to stepName
                ),
                type = MeterType.RATE
            )
        )
    }

    override fun rate(name: String, vararg tags: String): Rate {
        return rate(asId(name, tagsAsMap(tags), type = MeterType.RATE))
    }

    private fun rate(meterId: Meter.Id): Rate {
        return meterRegistry.rate(meterId)
    }

    override fun gauge(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
    ): Gauge {
        return gauge(
            Meter.Id(
                meterName = name,
                tags = tags + additionalTags + mapOf(
                    CAMPAIGN_KEY to currentCampaignKey!!,
                    SCENARIO_NAME to scenarioName,
                    STEP_NAME to stepName
                ),
                type = MeterType.GAUGE
            )
        )
    }

    private fun gauge(meterId: Meter.Id): Gauge {
        return meterRegistry.gauge(meterId)
    }

    override fun summary(name: String, vararg tags: String): DistributionSummary {
        val meterId = asId(name, tagsAsMap(tags), MeterType.DISTRIBUTION_SUMMARY)
        return summary(meterId, emptyList())
    }

    override fun throughput(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        unit: ChronoUnit,
        percentiles: Collection<Double>,
        tags: Map<String, String>,
    ): Throughput {
        return throughput(
            Meter.Id(
                meterName = name,
                tags = tags + additionalTags + mapOf(
                    CAMPAIGN_KEY to currentCampaignKey!!,
                    SCENARIO_NAME to scenarioName,
                    STEP_NAME to stepName
                ),
                type = MeterType.THROUGHPUT
            ),
            unit = unit,
            percentiles = percentiles
        )
    }

    override fun throughput(name: String, vararg tags: String): Throughput {
        return throughput(asId(name, tagsAsMap(tags), MeterType.THROUGHPUT), ChronoUnit.SECONDS, emptyList())
    }

    private fun throughput(
        meterId: Meter.Id,
        unit: ChronoUnit,
        percentiles: Collection<Double>,
    ): Throughput {
        return meterRegistry.throughput(
            meterId,
            unit,
            (percentiles.takeIf { it.isNotEmpty() }
                ?: measurementConfiguration.throughput.percentiles.orEmpty()).toSet())
    }

    override fun summary(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        percentiles: Collection<Double>,
    ): DistributionSummary {
        return summary(
            meterId = Meter.Id(
                meterName = name,
                tags = tags + additionalTags + mapOf(
                    CAMPAIGN_KEY to currentCampaignKey!!,
                    SCENARIO_NAME to scenarioName,
                    STEP_NAME to stepName
                ),
                type = MeterType.DISTRIBUTION_SUMMARY
            ),
            percentiles = percentiles,
        )
    }

    private fun summary(
        meterId: Meter.Id,
        percentiles: Collection<Double>,
    ): DistributionSummary {
        return meterRegistry.summary(
            meterId,
            (percentiles.takeIf { it.isNotEmpty() } ?: measurementConfiguration.summary.percentiles.orEmpty()).toSet())
    }

    override fun timer(name: String, vararg tags: String): Timer {
        val meterId = asId(name, tagsAsMap(tags), MeterType.TIMER)
        return timer(meterId, emptyList())
    }

    override fun timer(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        percentiles: Collection<Double>,
    ): Timer {
        return timer(
            meterId = Meter.Id(
                meterName = name,
                tags = tags + additionalTags + mapOf(
                    CAMPAIGN_KEY to currentCampaignKey!!,
                    SCENARIO_NAME to scenarioName,
                    STEP_NAME to stepName
                ),
                type = MeterType.TIMER
            ),
            percentiles = percentiles
        )
    }

    private fun timer(
        meterId: Meter.Id,
        percentiles: Collection<Double>,
    ): Timer {
        return meterRegistry.timer(
            meterId,
            (percentiles.takeIf { it.isNotEmpty() } ?: measurementConfiguration.timer.percentiles.orEmpty()).toSet())
    }

    /**
     * Converts the provided details to a meter id.
     */
    private fun asId(name: String, tags: Map<String, String>, type: MeterType): Meter.Id = Meter.Id(
        meterName = name,
        tags = tags + additionalTags,
        type = type
    )

    /**
     * Build a map of key/value pairs from a variable number of tags.
     */
    private fun tagsAsMap(tags: Array<out String>): Map<String, String> {
        return tags.toList().windowed(2, 2, false).associate {
            it[0] to it[1]
        }.let {
            if (currentCampaignKey != null) {
                it + (CAMPAIGN_KEY to currentCampaignKey!!) // The scenario and step tags should normally be already added by the caller function
            } else {
                it
            }
        }
    }

    companion object {

        val logger = logger()

        const val TENANT_KEY = "tenant"

        const val CAMPAIGN_KEY = "campaign"

        const val SCENARIO_NAME = "scenario"

        const val STEP_NAME = "step"
    }
}