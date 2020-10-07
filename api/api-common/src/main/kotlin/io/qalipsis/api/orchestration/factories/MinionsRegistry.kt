package io.qalipsis.api.orchestration.factories

import io.qalipsis.api.context.MinionId

/**
 *
 * @author Eric Jessé
 */
interface MinionsRegistry {

    operator fun get(minionId: MinionId): Collection<Minion>

    fun has(minionId: MinionId): Boolean
}
