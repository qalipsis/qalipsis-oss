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

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.context.annotation.Value
import io.qalipsis.api.config.MetersConfig
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterRegistryConfiguration
import io.qalipsis.api.meters.MeterRegistryFactory
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.meters.NoopMeterRegistry
import io.qalipsis.core.reporter.MeterReporter
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.ToDoubleFunction
import io.micrometer.core.instrument.DistributionSummary as MicrometerDistributionSummary
import io.micrometer.core.instrument.Gauge as MicrometerGauge
import io.micrometer.core.instrument.Timer as MicrometerTimer

/**
 * Implementation of meter registry.
 */
@Singleton
internal class CampaignMeterRegistryImpl(
    private val factories: Collection<MeterRegistryFactory>,
    private val factoryConfiguration: FactoryConfiguration,
    private val meterReporter: MeterReporter,
    @Value("\${${MetersConfig.EXPORT_CONFIGURATION}.campaign-step:PT5S}") private val step: Duration
) : CampaignMeterRegistry, CampaignLifeCycleAware {

    private var meterRegistry: MeterRegistry = NoopMeterRegistry()

    private val additionalTags = mutableListOf<Tag>()

    private val clock = Clock.SYSTEM

    private lateinit var currentCampaignKey: CampaignKey

    /**
     * Contains all the unique meters.
     */
    private val meters = ConcurrentHashMap<Meter.Id, Meter<*>>()

    override suspend fun init(campaign: Campaign) {
        meters.clear()

        currentCampaignKey = campaign.campaignKey
        if (factories.isNotEmpty()) {
            // Additional tags to force to all the created meters.
            factoryConfiguration.zone?.takeIf { it.isNotBlank() }?.let { zone ->
                additionalTags += Tag.of("zone", zone)
            }
            additionalTags += Tag.of("tenant", factoryConfiguration.tenant)
            additionalTags += Tag.of("campaign", campaign.campaignKey)

            val configuration = object : MeterRegistryConfiguration {
                override val step: Duration = this@CampaignMeterRegistryImpl.step
            }
            val meterRegistries = factories.map { it.getRegistry(configuration) }
            meterRegistry = CompositeMeterRegistry(clock, meterRegistries)
        }
    }

    override suspend fun close(campaign: Campaign) {
        clear()
        if (meterRegistry !is NoopMeterRegistry) {
            meterRegistry.close()
            meterRegistry = NoopMeterRegistry()
        }
    }

    override fun clear() {
        meters.clear()
        meterRegistry.clear()
    }

    override fun counter(name: String, vararg tags: String): Counter {
        return counter(name, tagsAsList(tags))
    }

    override fun counter(name: String, tags: Iterable<Tag>): Counter {
        return counter(asId(name, tags))
    }

    override fun counter(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>
    ): Counter {
        return counter(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags
            )
        )
    }

    private fun counter(meterId: Meter.Id): Counter {
        return meters.computeIfAbsent(meterId) {
            CounterImpl(
                meterRegistry.counter(meterId.meterName, meterId.tags.forMicrometer()),
                meterId,
                meterReporter
            )
        } as Counter
    }


    override fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T {
        gauge(asId(name, tags), number, Number::toDouble)
        return number
    }

    override fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T, valueFunction: ToDoubleFunction<T>): T {
        gauge(asId(name, tags), stateObject, valueFunction)
        return stateObject
    }

    override fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T {
        gauge(asId(name, tags), collection) { it.size.toDouble() }
        return collection
    }

    override fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T {
        gauge(asId(name, tags), map) { it.size.toDouble() }
        return map
    }

    override fun <T : Number> gauge(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        number: T
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags
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
        valueFunction: ToDoubleFunction<T>
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags
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
        collection: T
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags
            ),
            collection
        ) { it.size.toDouble() }
    }

    override fun <T : Map<*, *>> gaugeMapSize(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>,
        map: T
    ): Gauge {
        return gauge(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags
            ),
            map
        ) { it.size.toDouble() }
    }

    private fun <T> gauge(meterId: Meter.Id, stateObject: T, valueFunction: ToDoubleFunction<T>): Gauge {
        return meters.computeIfAbsent(meterId) {
            GaugeImpl(
                MicrometerGauge.builder(meterId.meterName, stateObject, valueFunction)
                    .tags(meterId.tags.forMicrometer())
                    .register(meterRegistry),
                meterId,
                meterReporter
            )
        } as Gauge
    }

    override fun summary(name: String, vararg tags: String): DistributionSummary {
        return summary(asId(name, tagsAsList(tags)))
    }

    override fun summary(name: String, tags: Iterable<Tag>): DistributionSummary {
        return summary(asId(name, tags))
    }

    override fun summary(
        scenarioName: ScenarioName,
        stepName: StepName,
        name: String,
        tags: Map<String, String>
    ): DistributionSummary {
        return summary(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags
            )
        )
    }

    private fun summary(meterId: Meter.Id): DistributionSummary {
        return meters.computeIfAbsent(meterId) {
            DistributionSummaryImpl(
                MicrometerDistributionSummary.builder(meterId.meterName)
                    .tags(meterId.tags.forMicrometer())
                    .register(meterRegistry),
                meterId,
                meterReporter
            )
        } as DistributionSummary
    }

    override fun timer(name: String, vararg tags: String): Timer {
        return timer(asId(name, tagsAsList(tags)))
    }

    override fun timer(name: String, tags: Iterable<Tag>): Timer {
        return timer(asId(name, tags))
    }

    override fun timer(scenarioName: ScenarioName, stepName: StepName, name: String, tags: Map<String, String>): Timer {
        return timer(
            Meter.Id(
                campaignKey = currentCampaignKey,
                scenarioName = scenarioName,
                stepName = stepName,
                meterName = name,
                tags = tags
            )
        )
    }

    private fun timer(meterId: Meter.Id): Timer {
        return meters.computeIfAbsent(meterId) {
            TimerImpl(
                MicrometerTimer.builder(meterId.meterName)
                    .tags(meterId.tags.forMicrometer())
                    .register(meterRegistry),
                meterId,
                meterReporter
            )
        } as Timer
    }

    /**
     * Convert the provided tags to be used for Micrometer.
     */
    private fun Map<String, String>.forMicrometer() = additionalTags + this.map { Tag.of(it.key, it.value) }

    /**
     * Converts the provided details to a [Meter.Id].
     */
    private fun asId(name: String, tags: Iterable<Tag>): Meter.Id = asId(name, tags.associate { it.key to it.value })


    /**
     * Converts the provided details to a [MeterId].
     */
    private fun asId(name: String, tags: Map<String, String>): Meter.Id = Meter.Id(
        campaignKey = currentCampaignKey,
        scenarioName = tags["scenario"] ?: "",
        stepName = tags["step"] ?: "",
        meterName = name,
        tags = tags
    )

    /**
     * Build a list of [Tag]s from a variable number of tags.
     */
    private fun tagsAsList(tags: Array<out String>): List<Tag> {
        return tags.toList().windowed(2, 2, false).map {
            Tag.of(it[0], it[1])
        }
    }


}