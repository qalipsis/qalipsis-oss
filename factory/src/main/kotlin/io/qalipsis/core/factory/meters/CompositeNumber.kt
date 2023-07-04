package io.qalipsis.core.factory.meters

import io.micrometer.core.instrument.Meter
import java.util.concurrent.ConcurrentHashMap

/**
 * Composite class to encapsulate a list of [Number]s.
 *
 * @author Joël Valère
 */
internal class CompositeNumber : Number() {

    private var scenarioStateObjects: ConcurrentHashMap<Meter.Id, Number> = ConcurrentHashMap()

    override fun toByte() = scenarioStateObjects.values.sumOf { it.toInt() }.toByte()

    override fun toChar() = scenarioStateObjects.values.sumOf { it.toInt() }.toChar()

    override fun toDouble() = scenarioStateObjects.values.sumOf { it.toDouble() }

    override fun toFloat() = scenarioStateObjects.values.sumOf { it.toDouble() }.toFloat()

    override fun toInt() = scenarioStateObjects.values.sumOf { it.toInt() }

    override fun toLong() = scenarioStateObjects.values.sumOf { it.toLong() }

    override fun toShort() = scenarioStateObjects.values.sumOf { it.toInt() }.toShort()

    /**
     * Add the state object attached to a scenario.
     */
    fun add(scenarioMeterId: Meter.Id, number: Number) {
        scenarioStateObjects.putIfAbsent(scenarioMeterId, number)
    }

    /**
     * Remove the state object attached to a scenario.
     */
    fun remove(scenarioMeterId: Meter.Id) {
        scenarioStateObjects.remove(scenarioMeterId)
    }

}