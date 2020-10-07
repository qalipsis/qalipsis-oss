package io.qalipsis.api.steps

import io.qalipsis.api.scenario.MutableScenarioSpecification
import io.qalipsis.api.scenario.scenario

/**
 * Simple step specification only used for tests.
 *
 * @author Eric Jessé
 */
class DummyStepSpecification : AbstractStepSpecification<Unit, Int, DummyStepSpecification>() {

    init {
        scenario = scenario("my-scenario") as MutableScenarioSpecification
    }
}

fun <INPUT> AbstractStepSpecification<*, INPUT, *>.dummy(): DummyStepSpecification {
    val step = DummyStepSpecification()
    this.add(step)
    return step
}
