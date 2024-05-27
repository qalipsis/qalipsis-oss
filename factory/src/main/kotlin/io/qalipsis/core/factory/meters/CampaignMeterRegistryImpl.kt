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
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.reporter.MeterReporter
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.fixedRateTimer

/**
 * Implementation of meter registry.
 */
@Singleton
internal class CampaignMeterRegistryImpl(
    private val publisherFactories: Collection<MeasurementPublisherFactory>,
    factoryConfiguration: FactoryConfiguration,
    private val meterReporter: MeterReporter,
    @Value("\${${MetersConfig.EXPORT_CONFIGURATION}.campaign-step:PT5S}") private val step: Duration,
    private val measurementConfiguration: MeasurementConfiguration,
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
) : CampaignMeterRegistry, CampaignLifeCycleAware {

    private val additionalTags = mutableMapOf<String, String>()

    private lateinit var currentCampaignKey: CampaignKey

    private var publishingJob: Job? = null

    private var snapshots = listOf<MeterSnapshot<*>>()

    private var publishers = listOf<MeasurementPublisher>()

    private val latch = Latch(true)

    /**
     * Contains all the unique meters.
     */
    val meters = HashMap<Meter.Id, Meter<*>>()

    init {
        additionalTags.putAll(factoryConfiguration.tags)
        if (publisherFactories.isNotEmpty()) {
            // Additional tags to force to all the created meters.
            additionalTags.putAll(factoryConfiguration.tags)
            factoryConfiguration.zone?.takeIf { it.isNotBlank() }?.let { zone ->
                additionalTags["zone"] = zone
            }
        }
    }

    override suspend fun init(campaign: Campaign) {
        meters.clear()
        currentCampaignKey = campaign.campaignKey
        publishers = publisherFactories.map { it.getPublisher() }
        publishers.forEach { it.init() }
        startPublishingJob()
    }

    suspend fun startPublishingJob() {
        logger.debug { "Starting publishing job" }
        require(publishingJob == null) { "Previous job has not yet completed" }
        publishingJob = coroutineScope.launch {
            latch.release()
            val stepMillis = step.toMillis()
            val initialDelayMillis: Long = stepMillis - System.currentTimeMillis() % stepMillis + 1
            fixedRateTimer(
                name = "meter-publishing",
                daemon = false,
                initialDelay = initialDelayMillis,
                period = stepMillis
            ) {
                launch {
                    snapshots = takeSnapshots(Instant.now(), meters).toList()
                    publishers.forEach {
                        logger.trace { "Publishing ${snapshots.size} snapshots with $it publisher." }
                        it.publish(snapshots)
                    }
                }
            }
        }
        latch.await()
    }

    private suspend fun takeSnapshots(
        timestamp: Instant,
        meters: Map<Meter.Id, Meter<*>>,
    ): Collection<MeterSnapshot<*>> =
        meters.values.map { it.buildSnapshot(timestamp) }

    override suspend fun close(campaign: Campaign) {
        clear()
        latch.cancel()
        publishingJob?.cancel()
        publishers.forEach { it.stop() }
    }

    override fun clear() {
        meters.clear()
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
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags + additionalTags,
                type = MeterType.COUNTER
            )
        )
    }

    private fun counter(meterId: Meter.Id): Counter {
        return meters.computeIfAbsent(meterId) {
            CounterImpl(
                meterId,
                meterReporter
            )
        } as Counter
    }


    override fun gauge(name: String, vararg tags: String): Gauge {
        return gauge(asId(name, tagsAsMap(tags), MeterType.GAUGE))
    }

    override fun gauge(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags + additionalTags,
                type = MeterType.GAUGE
            )
        )
    }

    private fun gauge(meterId: Meter.Id): Gauge {
        return meters.computeIfAbsent(meterId) {
            GaugeImpl(
                meterId,
                meterReporter
            )
        } as Gauge
    }

    override fun summary(name: String, vararg tags: String): DistributionSummary {
        val meterId = asId(name, tagsAsMap(tags), MeterType.DISTRIBUTION_SUMMARY)
        return summary(
            meterId.scenarioName,
            meterId.stepName,
            meterId.meterName,
            meterId.tags
        )
    }

    override fun summary(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        percentiles: Collection<Double>
    ): DistributionSummary {
        return summary(
            meterId = Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags + additionalTags,
                type = MeterType.DISTRIBUTION_SUMMARY
            ),
            percentiles = measurementConfiguration.summary.percentiles ?: percentiles,
        )
    }

    private fun summary(
        meterId: Meter.Id,
        percentiles: Collection<Double>
    ): DistributionSummary {
        return meters.computeIfAbsent(meterId) {
            DistributionSummaryImpl(
                meterId,
                meterReporter,
                percentiles = percentiles
            )
        } as DistributionSummary
    }

    override fun timer(name: String, vararg tags: String): Timer {
        val meterId = asId(name, tagsAsMap(tags), MeterType.TIMER)
        return timer(
            meterId.scenarioName,
            meterId.stepName,
            meterId.meterName,
            meterId.tags
        )
    }

    override fun timer(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        percentiles: Collection<Double>
    ): Timer {
        return timer(
            meterId = Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags + additionalTags,
                type = MeterType.TIMER
            ),
            percentiles = measurementConfiguration.timer.percentiles ?: percentiles,
        )
    }

    private fun timer(
        meterId: Meter.Id,
        percentiles: Collection<Double>,
    ): Timer {
        return meters.computeIfAbsent(meterId) {
            TimerImpl(
                meterId,
                meterReporter,
                percentiles = percentiles,
            )
        } as Timer
    }

    /**
     * Converts the provided details to a meter id.
     */
    private fun asId(name: String, tags: Map<String, String>, type: MeterType): Meter.Id = Meter.Id(
        campaignKey = currentCampaignKey,
        scenarioName = tags["scenario"] ?: "",
        stepName = tags["step"] ?: "",
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
        }
    }

    companion object {
        val logger = logger()
    }
}