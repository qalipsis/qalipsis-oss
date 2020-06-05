package io.evolue.api.processors

import io.evolue.test.io.readFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ScenarioAnnotationProcessorTest {

    /**
     * Verified methods are from the class [ScenarioClass].
     */
    @Test
    internal fun `annotated methods should have been listed at compilation time`() {
        val scenarioResources = this.javaClass.classLoader.getResources("META-INF/evolue/scenarios").toList()

        Assertions.assertEquals(1, scenarioResources.size)
        val scenarios = readFile(scenarioResources[0].openStream(), true, "#").toSet()

        val expected = setOf(
            "io.evolue.api.scenariosloader.ScenarioLoader22ef3757d76e36c8b1e006887edd5a49",
            "io.evolue.api.scenariosloader.ScenarioLoader56b4c7f6efe23ad99435b8f4dec3c4c5",
            "io.evolue.api.scenariosloader.ScenarioLoadera9da7f4b826e382385e921526b0325b9"
        )
        Assertions.assertEquals(expected, scenarios)
    }

    @Test
    internal fun `should load the annotated scenarios`() {
        ServicesLoader.loadServices<Any>("scenarios")

        val expected = setOf(
            "aMethodOutsideAClass was loaded",
            "aMethodInsideAnObject was loaded",
            "aMethodInsideAClass was loaded"
        )
        Assertions.assertEquals(expected, executedScenarios)
    }
}
