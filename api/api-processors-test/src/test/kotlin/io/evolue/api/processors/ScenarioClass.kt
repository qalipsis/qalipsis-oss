package io.evolue.api.processors

import io.evolue.api.annotations.Scenario

/**
 * @author Eric Jessé
 */

internal val executedScenarios = mutableSetOf<String>()

@Scenario
internal fun aMethodOutsideAClass() {
    executedScenarios.add("aMethodOutsideAClass was loaded")
}

internal class ScenarioClass {

    @Scenario
    fun aMethodInsideAClass() {
        executedScenarios.add("aMethodInsideAClass was loaded")
    }

    object ScenarioDeclaration {

        @Scenario
        fun aMethodInsideAnObject() {
            executedScenarios.add("aMethodInsideAnObject was loaded")
        }
    }
}
