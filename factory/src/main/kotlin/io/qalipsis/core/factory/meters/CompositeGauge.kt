package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.Meter

/**
 * Composite class to encapsulate the [Gauge]s at the scenario and campaign
 * level in order to update both at the same time.
 *
 * This [Gauge] should be seen as the [scenarioLevelGauge] by the calling application.
 * The meter registries are the only one aware of the existence of the [campaignLevelGauge]
 * and will ask for its publication when required.
 *
 * This instance of [Gauge] is not known by the instances of Qalipsis measurement publisher.
 *
 * @author Joël Valère
 */
internal data class CompositeGauge(
    private val scenarioLevelGauge: Gauge,
    private val campaignLevelGauge: Gauge
) : Gauge by scenarioLevelGauge {

    override val id: Meter.Id
        get() = scenarioLevelGauge.id

    override fun report(configure: Meter.ReportingConfiguration<Gauge>.() -> Unit): Gauge =
        scenarioLevelGauge.report(configure)

    fun toByte() = scenarioLevelGauge.value().toInt().toByte()

    fun toChar(): Char = scenarioLevelGauge.value().toInt().toChar()

    fun toDouble() = scenarioLevelGauge.value()

    fun toFloat() = scenarioLevelGauge.value().toFloat()

    fun toInt() = scenarioLevelGauge.value().toInt()

    fun toLong() = scenarioLevelGauge.value().toLong()

    fun toShort() = scenarioLevelGauge.value().toInt().toShort()

}