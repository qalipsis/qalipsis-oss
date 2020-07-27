package io.evolue.api.steps

import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.scenario.scenario

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
