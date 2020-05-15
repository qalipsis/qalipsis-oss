package io.evolue.api.states

import io.evolue.api.context.MinionId
import io.evolue.api.context.SharedStateName
import java.time.Duration

/**
 * Definition of a shared state,
 *
 * @author Eric Jessé
 */
data class SharedStateDefinition(
    /**
     * ID of the Minion attached to the state.
     */
    val minionId: MinionId,
    /**
     * Name of the shared state.
     */
    val sharedStateName: SharedStateName,
    /**
     * Time to live of the state until its eviction.
     * When no value is set, the behavior is undefined and let to the implementation of [SharedStateRegistry].
     */
    val timeToLive: Duration? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SharedStateDefinition

        if (minionId != other.minionId) return false
        if (sharedStateName != other.sharedStateName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = minionId.hashCode()
        result = 31 * result + sharedStateName.hashCode()
        return result
    }
}