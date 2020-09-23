package io.evolue.api.processors

import io.evolue.test.io.readFile
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyExactly
import io.evolue.test.mockk.verifyOnce
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.mockk.every
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Optional

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
            "io.evolue.api.scenariosloader.ScenarioClassKt\$aMethodOutsideAClass",
            "io.evolue.api.scenariosloader.ScenarioDeclaration\$aMethodInsideAnObject",
            "io.evolue.api.scenariosloader.ScenarioClass\$aMethodInsideAClass"
        )
        Assertions.assertEquals(expected, scenarios)
    }

    @Test
    internal fun `should load the annotated scenarios`() {
        val environment: Environment = relaxedMockk {
            every { getProperty(eq("this-is-a-test"), Duration::class.java) } returns Optional.of(Duration.ZERO)
        }
        val applicationContext: ApplicationContext = relaxedMockk {
            every { getBean(ClassToInject::class.java) } returns relaxedMockk()
            every { getBean(OtherClassToInject::class.java) } returns relaxedMockk()
            every { getEnvironment() } returns environment
        }

        ServicesLoader.loadServices<Any>("scenarios", applicationContext)

        val expected = setOf(
            "aMethodOutsideAClass was loaded",
            "aMethodInsideAnObject was loaded",
            "aMethodInsideAClass was loaded"
        )
        Assertions.assertEquals(expected, executedScenarios)

        verifyExactly(4) {
            applicationContext.getBean(ClassToInject::class.java)
            applicationContext.getBean(OtherClassToInject::class.java)
        }
        verifyOnce { environment.getProperty(eq("this-is-a-test"), Duration::class.java) }
    }
}
