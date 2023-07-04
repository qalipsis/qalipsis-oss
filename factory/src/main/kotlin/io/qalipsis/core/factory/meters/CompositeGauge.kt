package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter
import io.micrometer.core.instrument.Gauge as MicrometerGauge

/**
 * Composite class to encapsulate the [Gauge]s at the scenario and campaign
 * level in order to update both at the same time.
 *
 * This [Gauge] should be seen as the [scenarioLevelGauge] by the calling application.
 * The meter registries are the only one aware of the existence of the [campaignLevelGauge]
 * and will ask for its publication when required.
 *
 * This instance of [Gauge] is not known by the instances of [io.scenarioLevelGauge.core.instrument.MeterRegistry].
 *
 * @author Joël Valère
 */
internal data class CompositeGauge(
    private val scenarioLevelGauge: Gauge,
    private val campaignLevelGauge: MicrometerGauge
) : Gauge(), io.micrometer.core.instrument.Gauge by scenarioLevelGauge {

    override val id: Meter.Id
        get() = scenarioLevelGauge.id

    override fun report(configure: Meter.ReportingConfiguration<Gauge>.() -> Unit): Gauge =
        scenarioLevelGauge.report(configure)

    override fun toByte() = scenarioLevelGauge.toByte()

    override fun toChar(): Char = scenarioLevelGauge.toChar()

    override fun toDouble() = scenarioLevelGauge.value()

    override fun toFloat() = scenarioLevelGauge.toFloat()

    override fun toInt() = scenarioLevelGauge.toInt()

    override fun toLong() = scenarioLevelGauge.toLong()

    override fun toShort() = scenarioLevelGauge.toShort()

}