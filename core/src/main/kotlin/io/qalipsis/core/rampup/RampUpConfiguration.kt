package io.qalipsis.core.rampup

import kotlinx.serialization.Serializable

/**
 * Configuration of the ramp-up to apply to a scenario in a campaign.
 *
 * @property startOffsetMs time to wait before the first minion is executed, it should take the latency of the factories into consideration
 * @property speedFactor speed factor to apply on the ramp-up strategy, each strategy will apply it differently depending on its own implementation
 *
 * @author Eric Jessé
 */
@Serializable
data class RampUpConfiguration(
    val startOffsetMs: Long = 3000,
    val speedFactor: Double = 1.0,
)
