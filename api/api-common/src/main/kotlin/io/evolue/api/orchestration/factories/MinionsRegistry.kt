package io.evolue.api.orchestration.factories

import io.evolue.api.context.MinionId

/**
 *
 * @author Eric Jessé
 */
interface MinionsRegistry {

    operator fun get(minionId: MinionId): Minion?

    fun has(minionId: MinionId): Boolean
}
