package io.qalipsis.core.heartbeat

import io.qalipsis.api.context.CampaignId
import java.time.Instant

/**
 * Signal sent from the factory to the head on a regular basis to distribute the factory inner state.
 *
 * @author Eric Jessé
 */
data class Heartbeat(
    val nodeId: String,
    val timestamp: Instant,
    val state: STATE = STATE.HEALTHY,
    val campaignId: CampaignId? = null
) {

    enum class STATE {
        HEALTHY
    }

}
