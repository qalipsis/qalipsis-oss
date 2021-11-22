package io.qalipsis.core.factory.steps

import io.qalipsis.core.factory.orchestration.Runner

/**
 * Interface for a step to have an instance of [Runner] injected after the creation.
 *
 * @author Eric Jessé
 */
interface RunnerAware {

    var runner: Runner
}
