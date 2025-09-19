/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.meters.inmemory

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
import io.qalipsis.core.factory.meters.MeterRegistry
import io.qalipsis.core.factory.meters.inmemory.composite.CompositeCounter
import io.qalipsis.core.factory.meters.inmemory.composite.CompositeDistributionSummary
import io.qalipsis.core.factory.meters.inmemory.composite.CompositeGauge
import io.qalipsis.core.factory.meters.inmemory.composite.CompositeRate
import io.qalipsis.core.factory.meters.inmemory.composite.CompositeThroughput
import io.qalipsis.core.factory.meters.inmemory.composite.CompositeTimer
import io.qalipsis.core.factory.meters.inmemory.unpublished.UnpublishedCounter
import io.qalipsis.core.factory.meters.inmemory.unpublished.UnpublishedDistributionSummary
import io.qalipsis.core.factory.meters.inmemory.unpublished.UnpublishedGauge
import io.qalipsis.core.factory.meters.inmemory.unpublished.UnpublishedRate
import io.qalipsis.core.factory.meters.inmemory.unpublished.UnpublishedThroughput
import io.qalipsis.core.factory.meters.inmemory.unpublished.UnpublishedTimer
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryCumulativeCounter
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryCumulativeDistributionSummary
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryCumulativeRate
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryCumulativeThroughput
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryCumulativeTimer
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryGauge
import io.qalipsis.core.reporter.MeterReporter
import jakarta.inject.Singleton
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of [MeterRegistry] that stores the [Meter]s into memory.
 *
 * @author Eric Jessé
 */
@Singleton
class InMemoryCumulativeMeterRegistry(
    private val meterReporter: MeterReporter,
) : MeterRegistry, CampaignLifeCycleAware {

    /**
     * Contains all the unique meters.
     */
    @KTestable
    private val meters = ConcurrentHashMap<Meter.Id, Meter<*>>()

    /**
     * Cache to store global meters and prevent duplication.
     */
    @KTestable
    private val globalMeters = ConcurrentHashMap<Meter.Id, Meter<*>>()

    private var snapshotsConverter: (Collection<MeterSnapshot>) -> Collection<MeterSnapshot> = NO_OP_SNAPSHOTS_CONVERTER

    private var meterConverter: MeterConverter = NO_OP_SCENARIO_METER_CONVERTER

    override suspend fun init(campaign: Campaign) {
        meters.clear()
        globalMeters.clear()
        super.init(campaign)
        if (campaign.scenarios.size == 1) {
            // When there is a unique scenario running in the campaign, we let the meters being created normally.
            // The snapshots later generated for the scenario will be duplicated without scenario tag, as "company-level" measurements.
            meterConverter = NO_OP_SCENARIO_METER_CONVERTER
            snapshotsConverter = CAMPAIGN_WIDE_SNAPSHOTS_CONVERTER
        } else {
            // When there are several scenarios running in the campaign, the scenario-level meters are cloned without
            // scenario tag to create a campaign-level meter. Both are encapsulated into a composite meter and updated
            // simultaneously.
            snapshotsConverter = NO_OP_SNAPSHOTS_CONVERTER
            meterConverter = CompositeDecorator(meterReporter, globalMeters)
        }
    }

    override fun counter(meterId: Meter.Id): Counter {
        return globalMeters.getOrElse(meterId) {
            meters.computeIfAbsent(meterId) {
                meterConverter.decorate(InMemoryCumulativeCounter(meterId, meterReporter))
            }
        } as Counter
    }

    override fun timer(meterId: Meter.Id, percentiles: Set<Double>): Timer {
        return globalMeters.getOrElse(meterId) {
            meters.computeIfAbsent(meterId) {
                meterConverter.decorate(InMemoryCumulativeTimer(meterId, meterReporter, percentiles = percentiles))
            }
        } as Timer
    }

    override fun gauge(meterId: Meter.Id): Gauge {
        return globalMeters.getOrElse(meterId) {
            meters.computeIfAbsent(meterId) {
                meterConverter.decorate(InMemoryGauge(meterId, meterReporter))
            }
        } as Gauge
    }

    override fun summary(meterId: Meter.Id, percentiles: Set<Double>): DistributionSummary {
        return globalMeters.getOrElse(meterId) {
            meters.computeIfAbsent(meterId) {
                meterConverter.decorate(
                    InMemoryCumulativeDistributionSummary(
                        id = meterId,
                        meterReporter = meterReporter,
                        percentiles = percentiles
                    )
                )
            }
        } as DistributionSummary
    }

    override suspend fun snapshots(instant: Instant): Collection<MeterSnapshot> {
        return snapshotsConverter(meters.values.flatMap { meter -> meter.snapshot(instant) })
    }

    override fun rate(meterId: Meter.Id): Rate {
        return globalMeters.getOrElse(meterId) {
            meters.computeIfAbsent(meterId) {
                meterConverter.decorate(
                    InMemoryCumulativeRate(
                        id = meterId,
                        meterReporter = meterReporter
                    )
                )
            }
        } as Rate
    }

    override fun throughput(meterId: Meter.Id, unit: ChronoUnit, percentiles: Set<Double>): Throughput {
        return globalMeters.getOrElse(meterId) {
            meters.computeIfAbsent(meterId) {
                meterConverter.decorate(
                    InMemoryCumulativeThroughput(
                        id = meterId,
                        meterReporter = meterReporter,
                        unit = unit,
                        percentiles = percentiles
                    )
                )
            }
        } as Throughput
    }

    override suspend fun summarize(instant: Instant): Collection<MeterSnapshot> {
        return snapshotsConverter(meters.values.flatMap { meter -> meter.summarize(instant) })
    }

    override suspend fun close(campaign: Campaign) {
        meters.clear()
        globalMeters.clear()
        snapshotsConverter = NO_OP_SNAPSHOTS_CONVERTER
        meterConverter = NO_OP_SCENARIO_METER_CONVERTER
        super.close(campaign)
    }

    private interface MeterConverter {

        fun <T : Meter<*>> decorate(meter: T): T

    }

    /**
     * [MeterConverter] that wraps the expected scenario-based meter along with a meter of same type that
     * has a wider scope to also monitor data at the campaign level.
     * When the campaign-level meter was already attached to another meter, it is itself decorated into an
     * "unpublished" meter wrapper, in order to avoid the duplication of the generated [MeterSnapshot].
     */
    @Suppress("UNCHECKED_CAST")
    private class CompositeDecorator(
        val meterReporter: MeterReporter,
        val globalMeterCache: MutableMap<Meter.Id, Meter<*>>
    ) : MeterConverter {

        override fun <T : Meter<*>> decorate(meter: T): T {
            return if ("scenario" in meter.id.tags) {
                val globalTags = meter.id.tags - LOCALIZATION_AND_SCENARIO_TAGS
                val globalMeterId = meter.id.copy(tags = globalTags)

                when (meter) {
                    is Gauge -> {
                        val globalMeter = globalMeterCache.compute(globalMeterId)
                        { _, g ->
                            when (g) {
                                null -> InMemoryGauge(globalMeterId, meterReporter)
                                !is UnpublishedGauge -> UnpublishedGauge(g as Gauge)
                                else -> g
                            }
                        }
                        CompositeGauge(scenarioMeter = meter, globalMeter = globalMeter as Gauge)
                    }

                    is Timer -> {
                        val globalMeter = globalMeterCache.compute(globalMeterId)
                        { _, t ->
                            when (t) {
                                null -> InMemoryCumulativeTimer(
                                    globalMeterId,
                                    meterReporter,
                                    meter.percentiles.toSet()
                                )

                                !is UnpublishedTimer -> UnpublishedTimer(t as Timer)
                                else -> t
                            }
                        }
                        CompositeTimer(scenarioMeter = meter, globalMeter = globalMeter as Timer)
                    }

                    is DistributionSummary -> {
                        val globalMeter = globalMeterCache.compute(globalMeterId)
                        { _, t ->
                            when (t) {
                                null -> InMemoryCumulativeDistributionSummary(
                                    globalMeterId,
                                    meterReporter,
                                    meter.percentiles.toSet()
                                )

                                !is UnpublishedDistributionSummary -> UnpublishedDistributionSummary(t as DistributionSummary)
                                else -> t
                            }
                        }
                        CompositeDistributionSummary(
                            scenarioMeter = meter,
                            globalMeter = globalMeter as DistributionSummary
                        )
                    }

                    is Counter -> {
                        val globalMeter = globalMeterCache.compute(globalMeterId)
                        { _, t ->
                            when (t) {
                                null -> InMemoryCumulativeCounter(globalMeterId, meterReporter)
                                !is UnpublishedCounter -> UnpublishedCounter(t as Counter)
                                else -> t
                            }
                        }
                        CompositeCounter(scenarioMeter = meter, globalMeter = globalMeter as Counter)
                    }

                    is Rate -> {
                        val globalMeter = globalMeterCache.compute(globalMeterId)
                        { _, t ->
                            when (t) {
                                null -> InMemoryCumulativeRate(globalMeterId, meterReporter)
                                !is UnpublishedRate -> UnpublishedRate(t as Rate)
                                else -> t
                            }
                        }
                        CompositeRate(scenarioMeter = meter, globalMeter = globalMeter as Rate)
                    }

                    is Throughput -> {
                        val globalMeter = globalMeterCache.compute(globalMeterId)
                        { _, t ->
                            when (t) {
                                null -> InMemoryCumulativeThroughput(
                                    globalMeterId,
                                    meterReporter,
                                    meter.unit,
                                    meter.percentiles.toSet()
                                )

                                !is UnpublishedThroughput -> UnpublishedThroughput(t as Throughput)
                                else -> t
                            }
                        }
                        CompositeThroughput(scenarioMeter = meter, globalMeter = globalMeter as Throughput)
                    }

                    else -> IllegalArgumentException("The meter of type ${meter::class} is not supported")
                } as T
            } else {
                meter
            }
        }

    }

    private companion object {

        val log = logger()

        /**
         * Tags that specify the localization of the meter generation, that are to be removed
         * to create campaign-level meters.
         */
        val LOCALIZATION_AND_SCENARIO_TAGS = setOf("scenario", "zone")

        /**
         * Default converter for the generated snapshots, that does perform any change on them.
         */
        val NO_OP_SNAPSHOTS_CONVERTER: (Collection<MeterSnapshot>) -> Collection<MeterSnapshot> = { it }

        /**
         * Default converter for the created meters, that does not perform any change on them.
         */
        val NO_OP_SCENARIO_METER_CONVERTER: MeterConverter = object : MeterConverter {
            override fun <T : Meter<*>> decorate(meter: T): T {
                return meter
            }
        }

        val CAMPAIGN_WIDE_SNAPSHOTS_CONVERTER: (Collection<MeterSnapshot>) -> Collection<MeterSnapshot> =
            {
                it + it.filter { snapshot -> "scenario" in snapshot.meterId.tags }.map { snapshot ->
                    // Reuse the snapshot values but only remove the localization and scenario
                    val globalTags = snapshot.meterId.tags - LOCALIZATION_AND_SCENARIO_TAGS
                    snapshot.duplicate(meterId = snapshot.meterId.copy(tags = globalTags))
                }
            }
    }
}