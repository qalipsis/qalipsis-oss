package io.qalipsis.core.factories.steps

import io.qalipsis.core.factories.orchestration.Runner

/**
 * Interface for a step to have an instance of [Runner] injected after the creation.
 *
 * @author Eric Jessé
 */
interface RunnerAware {

    var runner: Runner
}
