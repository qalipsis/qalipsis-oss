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
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Meter.Id
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MeterRegistry.More
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.context.annotation.Value
import io.qalipsis.api.config.MetersConfig
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.MeterRegistryConfiguration
import io.qalipsis.api.meters.MeterRegistryFactory
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.meters.NoopMeterRegistry
import jakarta.inject.Singleton
import java.time.Duration
import java.util.function.Consumer
import java.util.function.ToDoubleFunction

/**
 * Implementation of meter registry
 */
@Singleton
internal class CampaignMeterRegistryImpl(
    private val factories: Collection<MeterRegistryFactory>,
    private val factoryConfiguration: FactoryConfiguration,
    @Value("\${${MetersConfig.EXPORT_CONFIGURATION}.campaign-step:PT5S}") private val step: Duration
) : CampaignMeterRegistry, CampaignLifeCycleAware {

    private var meterRegistry: MeterRegistry = NoopMeterRegistry()

    private val additionalTags = mutableListOf<Tag>()

    override suspend fun init(campaign: Campaign) {
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
            meterRegistry = CompositeMeterRegistry(Clock.SYSTEM, meterRegistries)
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
    }

    override fun counter(name: String, vararg tags: String): Counter {
        return counter(name, additionalTags + tagsAsList(tags))
    }

    private fun tagsAsList(tags: Array<out String>): List<Tag> {
        return tags.toList().windowed(2, 2, false).map {
            Tag.of(it[0], it[1])
        }
    }

    override fun counter(name: String, tags: Iterable<Tag>): Counter {
        return meterRegistry.counter(name, additionalTags + tags.toList())
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
        return meterRegistry.gauge(name, additionalTags + tags, number)!!
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

    override fun getMeters(): List<Meter> {
        return meterRegistry.meters
    }

    override fun more(): More {
        return meterRegistry.more()
    }

    override fun remove(meter: Meter): Meter {
        return meterRegistry.remove(meter)
    }

    override fun remove(mappedId: Id): Meter {
        // FIXME Add the additional tags.
        return meterRegistry.remove(mappedId)
    }

    override fun removeByPreFilterId(preFilterId: Id): Meter {
        // FIXME Add the additional tags.
        return meterRegistry.remove(preFilterId)
    }

    override fun summary(name: String, vararg tags: String): DistributionSummary {
        return meterRegistry.summary(name, additionalTags + tagsAsList(tags))
    }

    override fun summary(name: String, tags: Iterable<Tag>): DistributionSummary {
        return meterRegistry.summary(name, additionalTags + tags)
    }

    override fun timer(name: String, vararg tags: String): Timer {
        return meterRegistry.timer(name, additionalTags + tagsAsList(tags))
    }

    override fun timer(name: String, tags: Iterable<Tag>): Timer {
        return meterRegistry.timer(name, additionalTags + tags)
    }
}