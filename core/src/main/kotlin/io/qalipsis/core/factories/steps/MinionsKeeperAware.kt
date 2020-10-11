package io.qalipsis.core.factories.steps

import io.qalipsis.api.orchestration.factories.MinionsKeeper

/**
 * Interface for a step to have an instance of [MinionsKeeper] injected after the creation.
 *
 * @author Eric Jessé
 */
interface MinionsKeeperAware {

    var minionsKeeper: MinionsKeeper

}