package io.qalipsis.core.heartbeat

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.serialization.InstantKotlinSerializer
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
    val state: State = State.HEALTHY,
    val campaignKey: CampaignKey? = null
) {

    enum class State {
        REGISTERED, UNREGISTERED, HEALTHY, UNHEALTHY
    }

}
