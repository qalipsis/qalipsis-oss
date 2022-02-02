package io.qalipsis.core.heartbeat

import io.qalipsis.api.context.CampaignId
import io.qalipsis.core.serialization.InstantKotlinSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Signal sent from the factory to the head on a regular basis to distribute the factory inner state.
 *
 * @author Eric Jessé
 */
@Serializable
data class Heartbeat(
    val nodeId: String,
    @Serializable(with = InstantKotlinSerializer::class) val timestamp: Instant,
    val state: STATE = STATE.HEALTHY,
    val campaignId: CampaignId? = null
) {

    enum class STATE {
        REGISTERED, UNREGISTERED, HEALTHY, UNHEALTHY
    }

}
