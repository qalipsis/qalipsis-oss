package io.qalipsis.core.factory.steps

import io.qalipsis.core.factory.orchestration.MinionsKeeper

/**
 * Interface for a step to have an instance of [MinionsKeeper] injected after the creation.
 *
 * @author Eric Jessé
 */
interface MinionsKeeperAware {

    var minionsKeeper: MinionsKeeper

}