package io.qalipsis.core.factory.campaign

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.distribution.HistogramSnapshot

/**
 * Composite class to encapsulate the [DistributionSummary]s at the scenario and campaign
 * level in order to update both at the same time.
 *
 * This [DistributionSummary] should be seen as the [scenarioLevelSummary] by the calling application.
 * The meter registries are the only one aware of the existence of the [campaignLevelSummary]
 * and will ask for its publication when required.
 *
 * This instance of [DistributionSummary] is not known by the instances of [io.micrometer.core.instrument.MeterRegistry].
 *
 * @author Joël Valère
 */
internal data class CompositeDistributionSummary(
    private val scenarioLevelSummary: DistributionSummary,
    private val campaignLevelSummary: DistributionSummary
) : DistributionSummary {

    override fun getId(): Meter.Id = scenarioLevelSummary.id

    override fun takeSnapshot(): HistogramSnapshot = scenarioLevelSummary.takeSnapshot()

    override fun record(amount: Double) {
        scenarioLevelSummary.record(amount)
        campaignLevelSummary.record(amount)
    }

    override fun count() = scenarioLevelSummary.count()

    override fun totalAmount() = scenarioLevelSummary.totalAmount()

    override fun max() = scenarioLevelSummary.max()
}