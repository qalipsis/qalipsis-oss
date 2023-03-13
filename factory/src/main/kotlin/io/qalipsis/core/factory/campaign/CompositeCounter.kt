package io.qalipsis.core.factory.campaign

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Meter

/**
 * Composite class to encapsulate the [Counter]s at the scenario and campaign
 * level in order to update both at the same time.
 *
 * This [Counter] should be seen as the [scenarioLevelCounter] by the calling application.
 * The meter registries are the only one aware of the existence of the [campaignLevelCounter]
 * and will ask for its publication when required.
 *
 * This instance of [Counter] is not known by the instances of [io.micrometer.core.instrument.MeterRegistry].
 *
 * @author Joël Valère
 */
internal data class CompositeCounter(
    private val scenarioLevelCounter: Counter,
    private val campaignLevelCounter: Counter
) : Counter {

    override fun getId(): Meter.Id = scenarioLevelCounter.id

    override fun increment(amount: Double) {
        scenarioLevelCounter.increment(amount)
        campaignLevelCounter.increment(amount)
    }

    override fun count() = scenarioLevelCounter.count()
}