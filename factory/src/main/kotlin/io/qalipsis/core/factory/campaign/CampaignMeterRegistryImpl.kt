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

package io.qalipsis.core.factory.campaign

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Meter.Id
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MeterRegistry.More
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.context.annotation.Value
import io.qalipsis.api.config.MetersConfig
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.MeterRegistryConfiguration
import io.qalipsis.api.meters.MeterRegistryFactory
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.meters.NoopMeterRegistry
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.ToDoubleFunction

/**
 * Implementation of meter registry.
 */
@Singleton
internal class CampaignMeterRegistryImpl(
    private val factories: Collection<MeterRegistryFactory>,
    private val factoryConfiguration: FactoryConfiguration,
    @Value("\${${MetersConfig.EXPORT_CONFIGURATION}.campaign-step:PT5S}") private val step: Duration
) : CampaignMeterRegistry, CampaignLifeCycleAware {

    private var meterRegistry: MeterRegistry = NoopMeterRegistry()

    private val additionalTags = mutableListOf<Tag>()

    private val clock = Clock.SYSTEM

    /**
     * Campaign-global meters accessible by their IDs.
     */
    private val campaignMeters = ConcurrentHashMap<Id, Meter>()

    /**
     * Gauge numbers accessible by their gauge IDs.
     */
    private val campaignGaugeNumbers = ConcurrentHashMap<Id, CompositeNumber>()

    /**
     * Count of scenario-based meters accessible by the campaign global meter ID.
     */
    private val scenarioMetersByCampaign = ConcurrentHashMap<Id, MutableSet<Id>>()

    override suspend fun init(campaign: Campaign) {
        campaignMeters.clear()
        scenarioMetersByCampaign.clear()

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
        meterRegistry.clear()
        campaignMeters.clear()
        scenarioMetersByCampaign.clear()
    }

    override fun counter(name: String, vararg tags: String): Counter {
        val counter = meterRegistry.counter(name, additionalTags + tagsAsList(tags))
        return if (tags.any { it == "scenario" }) {
            associateWithCampaignLevelMeter(counter) {
                CompositeCounter(scenarioLevelCounter = counter, campaignLevelCounter = it as Counter)
            }
        } else {
            counter
        }
    }

    override fun counter(name: String, tags: Iterable<Tag>): Counter {
        val counter = meterRegistry.counter(name, additionalTags + tags.toList())
        return if (tags.any { it.key == "scenario" }) {
            associateWithCampaignLevelMeter(counter) {
                CompositeCounter(scenarioLevelCounter = counter, campaignLevelCounter = it as Counter)
            }
        } else {
            counter
        }
    }

    override fun forEachMeter(consumer: Consumer<in Meter>) {
        meterRegistry.forEachMeter { consumer.accept(it) }
    }

    override fun <T : Number> gauge(name: String, number: T): T {
        return meterRegistry.gauge(name, additionalTags, number)!!
    }

    override fun <T> gauge(name: String, stateObject: T, valueFunction: ToDoubleFunction<T>): T {
        return meterRegistry.gauge(name, additionalTags, stateObject, valueFunction)!!
    }

    override fun <T : Number> gauge(name: String, tags: Iterable<Tag>, number: T): T {
        val gauge = Gauge.builder(name, number, Number::toDouble).tags(tags).register(meterRegistry)
        if (tags.any { it.key == "scenario" }) {
            val campaignMeterId = buildCampaignGlobalId(gauge.id)
            scenarioMetersByCampaign.computeIfAbsent(campaignMeterId) { concurrentSet() }.add(gauge.id)

            // Embed the campaign-level meter (create or use the existing one) in the returned value.
            campaignGaugeNumbers.computeIfAbsent(campaignMeterId) {
                meterRegistry.gauge(
                    name,
                    additionalTags + tags.filterNot { it.key == "scenario" },
                    CompositeNumber()
                )!!
            }.add(gauge.id, number)
        }
        return number
    }

    override fun <T> gauge(name: String, tags: Iterable<Tag>, stateObject: T, valueFunction: ToDoubleFunction<T>): T {
        return meterRegistry.gauge(name, additionalTags + tags, stateObject, valueFunction)!!
    }

    override fun <T : Collection<*>> gaugeCollectionSize(name: String, tags: Iterable<Tag>, collection: T): T {
        return meterRegistry.gaugeCollectionSize(name, additionalTags + tags, collection)!!
    }

    override fun <T : Map<*, *>> gaugeMapSize(name: String, tags: Iterable<Tag>, map: T): T {
        return meterRegistry.gaugeMapSize(name, additionalTags + tags, map)!!
    }

    override fun summary(name: String, vararg tags: String): DistributionSummary {
        val summary = meterRegistry.summary(name, additionalTags + tagsAsList(tags))
        return if (tags.any { it == "scenario" }) {
            associateWithCampaignLevelMeter(summary) {
                CompositeDistributionSummary(
                    scenarioLevelSummary = summary,
                    campaignLevelSummary = it as DistributionSummary
                )
            }
        } else {
            summary
        }
    }

    override fun summary(name: String, tags: Iterable<Tag>): DistributionSummary {
        val summary = meterRegistry.summary(name, additionalTags + tags)
        return if (tags.any { it.key == "scenario" }) {
            associateWithCampaignLevelMeter(summary) {
                CompositeDistributionSummary(
                    scenarioLevelSummary = summary,
                    campaignLevelSummary = it as DistributionSummary
                )
            }
        } else {
            summary
        }
    }

    override fun timer(name: String, vararg tags: String): Timer {
        val timer = meterRegistry.timer(name, additionalTags + tagsAsList(tags))
        return if (tags.any { it == "scenario" }) {
            associateWithCampaignLevelMeter(timer) {
                CompositeTimer(
                    clock = clock,
                    scenarioLevelTimer = timer,
                    campaignLevelTimer = it as Timer
                )
            }
        } else {
            timer
        }
    }

    override fun timer(name: String, tags: Iterable<Tag>): Timer {
        val timer = meterRegistry.timer(name, additionalTags + tags)
        return if (tags.any { it.key == "scenario" }) {
            associateWithCampaignLevelMeter(timer) {
                CompositeTimer(
                    clock = clock,
                    scenarioLevelTimer = timer,
                    campaignLevelTimer = it as Timer
                )
            }
        } else {
            timer
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Meter> associateWithCampaignLevelMeter(meter: T, compositeCreator: (campaignMeter: T) -> T): T {
        val campaignMeterId = buildCampaignGlobalId(meter.id)
        scenarioMetersByCampaign.computeIfAbsent(campaignMeterId) { concurrentSet() }.add(meter.id)

        // Embed the campaign-level meter (create or use the existing one) in the returned value.
        val campaignMeter = campaignMeters.computeIfAbsent(campaignMeterId) {
            when (meter) {
                is Counter -> meterRegistry.counter(campaignMeterId.name, campaignMeterId.tags)
                is Timer -> meterRegistry.timer(campaignMeterId.name, campaignMeterId.tags)
                is DistributionSummary -> meterRegistry.summary(campaignMeterId.name, campaignMeterId.tags)
                else -> throw IllegalArgumentException("Unknown meter type")
            }
        }
        return compositeCreator(campaignMeter as T)
    }

    override fun getMeters(): List<Meter> {
        return meterRegistry.meters
    }

    override fun more(): More {
        return meterRegistry.more()
    }

    override fun remove(meter: Meter): Meter {
        removeCampaignMeterIfRequired(meter.id)
        return meterRegistry.remove(meter)!!
    }

    override fun remove(mappedId: Id): Meter {
        removeCampaignMeterIfRequired(mappedId)
        return meterRegistry.remove(
            Id(
                mappedId.name,
                Tags.of(mappedId.tags + additionalTags),
                mappedId.baseUnit,
                mappedId.description,
                mappedId.type
            )
        )!!
    }

    override fun removeByPreFilterId(preFilterId: Id): Meter {
        return remove(preFilterId)
    }

    /**
     * Build a list of [Tag]s from a variable number of tags.
     */
    private fun tagsAsList(tags: Array<out String>): List<Tag> {
        return tags.toList().windowed(2, 2, false).map {
            Tag.of(it[0], it[1])
        }
    }

    /**
     * Build the meter's ID of a campaign.
     */
    private fun buildCampaignGlobalId(scenarioMeterId: Id) = Id(
        scenarioMeterId.name,
        Tags.of(scenarioMeterId.tags.filterNot { it.key == "scenario" }),
        scenarioMeterId.baseUnit,
        scenarioMeterId.description,
        scenarioMeterId.type
    )

    /**
     * Check if the meter to delete contains a scenario as tag
     * and if its ID is the last value in [scenarioMetersByCampaign].
     */
    private fun removeCampaignMeterIfRequired(mappedId: Id) {
        if (mappedId.tags.any { it.key == "scenario" }) {
            val campaignMeterId = buildCampaignGlobalId(mappedId)
            // Remove the scenario gauge from the campaign-level.
            campaignGaugeNumbers[campaignMeterId]?.remove(mappedId)

            // Remove the campaign meter only if there is no scenario-level meter left.
            if (scenarioMetersByCampaign[campaignMeterId]
                    ?.let { it.remove(mappedId) && it.size == 0 } == true
            ) {
                scenarioMetersByCampaign.remove(campaignMeterId)
                campaignMeters.remove(campaignMeterId)
                meterRegistry.remove(campaignMeterId)
            }

        }
    }
}