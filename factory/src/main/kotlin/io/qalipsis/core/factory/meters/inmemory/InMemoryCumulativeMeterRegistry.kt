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
import io.qalipsis.core.factory.meters.CompositeCounter
import io.qalipsis.core.factory.meters.CompositeDistributionSummary
import io.qalipsis.core.factory.meters.CompositeGauge
import io.qalipsis.core.factory.meters.CompositeTimer
import io.qalipsis.core.factory.meters.MeterRegistry
import io.qalipsis.core.reporter.MeterReporter
import jakarta.inject.Singleton
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Default implementation of [MeterRegistry] that stores the [Meter]s into memory.
 *
 * @author Eric Jessé
 */
@Singleton
internal class InMemoryCumulativeMeterRegistry(
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
        return snapshotsConverter(meters.values.map { meter -> meter.snapshot(instant) })
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
        return snapshotsConverter(meters.values.map { meter -> meter.summarize(instant) })
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

    @Suppress("UNCHECKED_CAST")
    private class CompositeDecorator(
        val meterReporter: MeterReporter,
        val globalMeterCache: ConcurrentMap<Meter.Id, Meter<*>>
    ) :
        MeterConverter {

        override fun <T : Meter<*>> decorate(meter: T): T {
            return if ("scenario" in meter.id.tags) {
                val globalTags = meter.id.tags - LOCALIZATION_AND_SCENARIO_TAGS
                val globalMeterId = meter.id.copy(tags = globalTags)

                when (meter) {
                    is Gauge -> {
                        val globalMeter = globalMeterCache.computeIfAbsent(globalMeterId) {
                            InMemoryGauge(
                                globalMeterId,
                                meterReporter
                            )
                        }
                        CompositeGauge(scenarioMeter = meter, globalMeter = globalMeter as Gauge)
                    }

                    is Timer -> {
                        val globalMeter = globalMeterCache.computeIfAbsent(globalMeterId) {
                            InMemoryCumulativeTimer(
                                globalMeterId,
                                meterReporter,
                                meter.percentiles.toSet()
                            )
                        }
                        CompositeTimer(
                            scenarioMeter = meter,
                            globalMeter = globalMeter as Timer
                        )
                    }

                    is DistributionSummary -> {
                        val globalMeter = globalMeterCache.computeIfAbsent(
                            globalMeterId
                        ) {
                            InMemoryCumulativeDistributionSummary(
                                globalMeterId,
                                meterReporter,
                                meter.percentiles.toSet()
                            )
                        }
                        CompositeDistributionSummary(
                            scenarioMeter = meter,
                            globalMeter = globalMeter as DistributionSummary
                        )
                    }

                    is Counter -> {
                        val globalMeter = globalMeterCache.computeIfAbsent(
                            globalMeterId
                        ) { InMemoryCumulativeCounter(globalMeterId, meterReporter) }
                        CompositeCounter(
                            scenarioMeter = meter,
                            globalMeter = globalMeter as Counter
                        )
                    }

                    is Rate -> {
                        val globalMeter = globalMeterCache.computeIfAbsent(
                            globalMeterId
                        ) { InMemoryCumulativeRate(globalMeterId, meterReporter) }
                        CompositeRate(
                            scenarioMeter = meter,
                            globalMeter = globalMeter as Rate
                        )
                    }

                    is Throughput -> {
                        val globalMeter = globalMeterCache.computeIfAbsent(
                            globalMeterId
                        ) {
                            InMemoryCumulativeThroughput(
                                globalMeterId,
                                meterReporter,
                                meter.unit,
                                meter.percentiles.toSet()
                            )
                        }
                        CompositeThroughput(
                            scenarioMeter = meter,
                            globalMeter = globalMeter as Throughput
                        )
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