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
import io.qalipsis.api.meters.MeterSnapShotImpl
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.reporter.MeterReporter
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.function.ToDoubleFunction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.fixedRateTimer

/**
 * Implementation of meter registry.
 */
@Singleton
internal class CampaignMeterRegistryImpl(
    private val factories: Collection<MeasurementPublisherFactory>,
    private val factoryConfiguration: FactoryConfiguration,
    private val meterReporter: MeterReporter,
    @Value("\${${MetersConfig.EXPORT_CONFIGURATION}.campaign-step:PT5S}") private val step: Duration,
    //@TODO thinking to use a background scope in here
    private val coroutineScope: CoroutineScope,
) : CampaignMeterRegistry, CampaignLifeCycleAware {

    private val additionalTags = mutableMapOf<String, String>()

    private lateinit var currentCampaignKey: CampaignKey

    private var publishingJob: Job? = null

    private var snapshots = listOf<MeterSnapshot<*>>()

    private var publishers = listOf<MeasurementPublisher>()

    /**
     * Contains all the unique meters.
     */
    val meters = HashMap<Meter.Id, Meter<*>>()

    override suspend fun init(campaign: Campaign) {
        meters.clear()

        currentCampaignKey = campaign.campaignKey
        if (factories.isNotEmpty()) {
            // Additional tags to force to all the created meters.
            factoryConfiguration.zone?.takeIf { it.isNotBlank() }?.let { zone ->
                additionalTags["zone"] = zone
            }
            additionalTags["tenant"] = factoryConfiguration.tenant
            additionalTags["campaign"] = campaign.campaignKey
            publishers = factories.map { it.getPublisher() }
        }
        publishers.forEach{it.init()}
        startPublishingJob()
    }

    suspend fun startPublishingJob() {
        //assert that a previous job has been put to a stop
        logger.info("")
        require(publishingJob == null) { "" }
        val countDownLatch = CountDownLatch(1)
        publishingJob = coroutineScope.launch {
            countDownLatch.countDown()
            val stepMillis = step.toMillis()
            val initialDelayMillis: Long = stepMillis - System.currentTimeMillis() % stepMillis + 1
            fixedRateTimer(name = "", daemon = false, initialDelay = initialDelayMillis, period = stepMillis) {
                launch {
                    snapshots = takeSnapshots(meters).toList()
                    publishers.forEach {
                        logger.trace { "Publishing ${snapshots.size} snapshots with $it publisher." }
                        it.publish(snapshots)
                    }
                }
            }
        }
        countDownLatch.await()
    }


    //@TODO fix this method and remove unnecessary duplicates
    private suspend fun takeSnapshots(meters: Map<Meter.Id, Meter<*>>): Collection<MeterSnapshot<*>> {
        val measurements = mutableListOf<MeterSnapshot<*>>()
        for ((_, meter) in meters) {
            val snapshot = when (meter) {
                is Counter -> MeterSnapShotImpl(meter, meter.measure())
                is Gauge -> MeterSnapShotImpl(meter, meter.measure())
                //pass in time unit
                is Timer -> {
                    MeterSnapShotImpl(meter, meter.measure())
                }

                is DistributionSummary -> {
                    MeterSnapShotImpl(meter, meter.measure())
                }

                else -> {
                    throw Exception("Unknown Meter Type")
                }
            }
            measurements.add(snapshot)
        }
        return measurements
    }

    override suspend fun close(campaign: Campaign) {
        clear()
        //  check if enabled publishing is still true then publishSafely
        publishingJob?.cancel()
        publishers.forEach { it.stop() }
    }

    override fun clear() {
        meters.clear()
    }

    override fun counter(name: String, vararg tags: String): Counter {
        return counter(name, tagsAsList(tags))
    }

    override fun counter(name: String, tags: Map<String, String>): Counter {
        return counter(asId(name, tags, MeterType.COUNTER))
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
                tags = tags,
                type = MeterType.COUNTER
            )
        )
    }

    private fun counter(meterId: Meter.Id): Counter {
        //find a way to store unique meters with same id: e.g guage & counter
        //@TODO verify that for all the meters, the addition of types of meters here makes changes on the meter id, if not go back to comment one above
        return meters.computeIfAbsent(meterId) {
            CounterImpl(
                meterId,
                meterReporter
            )
        } as Counter
    }


    override fun <T : Number> gauge(name: String, tags: Map<String, String>, number: T): T {
        gauge(asId(name, tags, MeterType.GAUGE), number, Number::toDouble)
        return number
    }

    override fun <T> gauge(
        name: String,
        tags: Map<String, String>,
        stateObject: T,
        valueFunction: ToDoubleFunction<T>,
    ): T {
        gauge(asId(name, tags, MeterType.GAUGE), stateObject, valueFunction)
        return stateObject
    }

    override fun <T : Collection<*>> gaugeCollectionSize(
        name: String,
        tags: Map<String, String>,
        collection: T,
    ): T {
        gauge(asId(name, tags, MeterType.GAUGE), collection) { it.size.toDouble() }
        return collection
    }

    override fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Map<String, String>, map: T): T {
        gauge(asId(name, tags, MeterType.GAUGE), map) { it.size.toDouble() }
        return map
    }

    override fun <T : Number> gauge(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        number: T,
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags,
                type = MeterType.GAUGE
            ),
            number,
            Number::toDouble
        )
    }

    override fun <T> gauge(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        stateObject: T,
        valueFunction: ToDoubleFunction<T>,
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags,
                type = MeterType.GAUGE
            ),
            stateObject,
            valueFunction
        )
    }

    override fun <T : Collection<*>> gaugeCollectionSize(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        collection: T,
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags,
                type = MeterType.GAUGE
            ),
            collection
        ) { it.size.toDouble() }
    }

    override fun <T : Map<*, *>> gaugeMapSize(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        map: T,
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags,
                type = MeterType.GAUGE
            ),
            map
        ) { it.size.toDouble() }
    }

    private fun <T> gauge(meterId: Meter.Id, stateObject: T, valueFunction: ToDoubleFunction<T>): Gauge {
        return meters.computeIfAbsent(meterId) {
            GaugeImpl(
                meterId,
                meterReporter
            ) { valueFunction.applyAsDouble(stateObject) }
        } as Gauge
    }

    override fun summary(name: String, vararg tags: String): DistributionSummary {
        return summary(asId(name, tagsAsList(tags), MeterType.DISTRIBUTION_SUMMARY))
    }

    override fun summary(name: String, tags: Map<String, String>): DistributionSummary {
        return summary(asId(name, tags, MeterType.DISTRIBUTION_SUMMARY))
    }

    override fun summary(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
    ): DistributionSummary {
        return summary(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags,
                type = MeterType.DISTRIBUTION_SUMMARY
            )
        )
    }

    private fun summary(meterId: Meter.Id): DistributionSummary {
        return meters.computeIfAbsent(meterId) {
            DistributionSummaryImpl(
                meterId,
                meterReporter
            )
        } as DistributionSummary
    }

    override fun timer(name: String, vararg tags: String): Timer {
        return timer(asId(name, tagsAsList(tags), MeterType.TIMER))
    }

    override fun timer(name: String, tags: Map<String, String>): Timer {
        return timer(asId(name, tags, MeterType.TIMER))
    }

    override fun timer(scenarioName: ScenarioName, stepName: StepName, name: String, tags: Map<String, String>): Timer {
        return timer(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags,
                type = MeterType.TIMER
            )
        )
    }

    private fun timer(meterId: Meter.Id): Timer {
        return meters.computeIfAbsent(meterId) {
            TimerImpl(
                meterId,
                meterReporter
            )
        } as Timer
    }

    /**
     * Converts the provided details to a [MeterId].
     */
    private fun asId(name: String, tags: Map<String, String>, type: MeterType): Meter.Id = Meter.Id(
        campaignKey = currentCampaignKey,
        scenarioName = tags["scenario"] ?: "",
        stepName = tags["step"] ?: "",
        meterName = name,
        tags = tags,
        type = type
    )

    /**
     * Build a map of key/value pairs from a variable number of tags.
     */
    private fun tagsAsList(tags: Array<out String>): Map<String, String> {
        return tags.toList().windowed(2, 2, false).associate {
            it[0] to it[1]
        }
    }

    companion object {
        val logger = logger()
    }
}