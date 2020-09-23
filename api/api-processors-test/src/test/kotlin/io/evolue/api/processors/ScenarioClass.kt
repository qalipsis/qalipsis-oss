package io.evolue.api.processors

import io.evolue.api.annotations.Property
import io.evolue.api.annotations.Scenario
import java.time.Duration

/**
 * @author Eric Jessé
 */

internal val executedScenarios = mutableSetOf<String>()

@Scenario
internal fun aMethodOutsideAClass(classToInject: ClassToInject, otherClassToInject: OtherClassToInject) {
    executedScenarios.add("aMethodOutsideAClass was loaded")
}

internal class ScenarioClass(classToInject: ClassToInject, otherClassToInject: OtherClassToInject) {

    @Scenario
    fun aMethodInsideAClass(classToInject: ClassToInject, otherClassToInject: OtherClassToInject) {
        executedScenarios.add("aMethodInsideAClass was loaded")
    }

    object ScenarioDeclaration {

        @Scenario
        fun aMethodInsideAnObject(classToInject: ClassToInject, otherClassToInject: OtherClassToInject,
                                  @Property("this-is-a-test") duration: Duration) {
            executedScenarios.add("aMethodInsideAnObject was loaded")
        }
    }
}
