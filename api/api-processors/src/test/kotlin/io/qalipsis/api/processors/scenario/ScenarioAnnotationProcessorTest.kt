/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.processors.scenario

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.key
import assertk.assertions.matches
import assertk.assertions.prop
import io.micronaut.inject.qualifiers.Qualifiers
import io.mockk.every
import io.qalipsis.api.scenario.Injector
import io.qalipsis.api.scenario.ScenarioLoader
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
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
        val scenariosUrl = this.javaClass.classLoader.getResource("META-INF/services/qalipsis/scenarios")
        Assertions.assertNotNull(scenariosUrl)

        val scenariosDir = File(scenariosUrl!!.toURI())
        val jsonFiles = scenariosDir.listFiles { file -> file.name.endsWith(".json") }!!
            .associate { it.name.removeSuffix(".json") to it.readText(StandardCharsets.UTF_8) }

        Assertions.assertEquals(3, jsonFiles.size)

        // Verify file names correspond to the loader class names.
        assertThat(jsonFiles.keys).containsOnly(
            "io.qalipsis.api.scenariosloader.ScenarioClassKt\$\$aMethodOutsideAClass",
            "io.qalipsis.api.scenariosloader.ScenarioClass\$\$aMethodInsideAClass",
            "io.qalipsis.api.scenariosloader.ScenarioDeclaration\$\$aMethodInsideAnObject"
        )

        // Verify JSON content of each scenario file.
        val outsideAClass = jsonFiles["io.qalipsis.api.scenariosloader.ScenarioClassKt\$\$aMethodOutsideAClass"]!!
        assertThat(outsideAClass).all {
            transform { it.contains(""""name": "a-method-outside-a-class"""") }.isEqualTo(true)
            transform { it.contains(""""description": "Scenario in a file"""") }.isEqualTo(true)
            transform { it.contains(""""version": "0.1"""") }.isEqualTo(true)
            transform { it.contains(""""loader": "io.qalipsis.api.scenariosloader.ScenarioClassKt${'$'}${'$'}aMethodOutsideAClass"""") }.isEqualTo(
                true
            )
        }

        val insideAClass = jsonFiles["io.qalipsis.api.scenariosloader.ScenarioClass\$\$aMethodInsideAClass"]!!
        assertThat(insideAClass).all {
            transform { it.contains(""""name": "a-method-inside-a-class"""") }.isEqualTo(true)
            transform { it.contains(""""description": "Scenario in a class"""") }.isEqualTo(true)
            transform { it.contains(""""version": "0.2.3"""") }.isEqualTo(true)
            transform { it.contains(""""loader": "io.qalipsis.api.scenariosloader.ScenarioClass${'$'}${'$'}aMethodInsideAClass"""") }.isEqualTo(
                true
            )
        }

        val insideAnObject = jsonFiles["io.qalipsis.api.scenariosloader.ScenarioDeclaration\$\$aMethodInsideAnObject"]!!
        assertThat(insideAnObject).all {
            transform { it.contains(""""name": "a-method-inside-an-object"""") }.isEqualTo(true)
            transform { it.contains(""""description": """"") }.isEqualTo(true)
            transform { it.contains(""""version": "1.34"""") }.isEqualTo(true)
            transform { it.contains(""""loader": "io.qalipsis.api.scenariosloader.ScenarioDeclaration${'$'}${'$'}aMethodInsideAnObject"""") }.isEqualTo(
                true
            )
        }
    }

    @Test
    internal fun `should load the annotated scenarios`() {
        // given
        val classToInject: ClassToInject = relaxedMockk()
        val otherClassToInject: OtherClassToInject = relaxedMockk()
        val interfaceToInject: InterfaceToInject = relaxedMockk()
        val injectablesAsList = listOf(classToInject, otherClassToInject)
        val namedInjectablesAsList = listOf(otherClassToInject)

        val injector: Injector = relaxedMockk {
            every { conversionService.convertRequired(eq("10"), eq(Integer.TYPE)) } returns 10
            every {
                conversionService.convertRequired(
                    setOf(classToInject, otherClassToInject),
                    eq(List::class.java)
                )
            } returns injectablesAsList
            every {
                conversionService.convertRequired(
                    setOf(otherClassToInject),
                    eq(List::class.java)
                )
            } returns namedInjectablesAsList

            every { getBean(ClassToInject::class.java) } returns classToInject
            every { getBean(OtherClassToInject::class.java) } returns otherClassToInject
            every { findBean(eq(OtherClassToInject::class.java)) } returns Optional.of(otherClassToInject)
            every {
                getBean(
                    InterfaceToInject::class.java,
                    Qualifiers.byName("myInjectable")
                )
            } returns interfaceToInject
            every { getBeansOfType(InterfaceToInject::class.java) } returns setOf(classToInject, otherClassToInject)
            every { getBeansOfType(InterfaceToInject::class.java, Qualifiers.byName("myInjectable")) } returns setOf(
                otherClassToInject
            )
            every { getRequiredProperty(eq("this-is-a-test"), eq(Duration::class.java)) } returns Duration.ofMillis(123)
            every { getProperty(eq("this-is-another-test"), eq(Integer.TYPE), eq(10)) } returns 5
            every {
                getProperty(
                    eq("this-is-yet-another-test"),
                    eq(String::class.java)
                )
            } returns Optional.of("No value")
        }

        // when
        val beforeLoad = Instant.now()
        val scenariosLoader = createScenariosLoader()
        scenariosLoader.forEach { scenariosProvider ->
            assertThat(scenariosProvider).all {
                prop(ScenarioLoader::name).isNotNull().matches(Regex("^[-0-9a-z]+$"))
                prop(ScenarioLoader::version).isNotNull().isNotEmpty()
                prop(ScenarioLoader::builtAt).isLessThan(beforeLoad)
            }

            // Trigger the loading of the scenario.
            scenariosProvider.load(injector)
        }

        assertThat(injectedForMethodOutsideAClass).all {
            key("classToInject").isSameAs(classToInject)
            key("mayBeOtherClassToInject").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isSameAs(otherClassToInject)
            key("injectables").isSameAs(injectablesAsList)
            key("namedInjectables").isSameAs(namedInjectablesAsList)
            key("property").isEqualTo(Duration.ofMillis(123))
            key("propertyWithDefaultValue").isEqualTo(5)
            key("mayBeProperty").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isEqualTo("No value")
        }

        assertThat(injectedIntoConstructor).all {
            key("classToInject").isSameAs(classToInject)
            key("mayBeOtherClassToInject").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isSameAs(otherClassToInject)
            key("injectables").isSameAs(injectablesAsList)
            key("namedInjectables").isSameAs(namedInjectablesAsList)
            key("property").isEqualTo(Duration.ofMillis(123))
            key("propertyWithDefaultValue").isEqualTo(5)
            key("mayBeProperty").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isEqualTo("No value")
        }

        assertThat(injectedIntoScenarioOfClass).all {
            key("classToInject").isSameAs(classToInject)
            key("mayBeOtherClassToInject").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isSameAs(otherClassToInject)
            key("injectables").isSameAs(injectablesAsList)
            key("namedInjectables").isSameAs(namedInjectablesAsList)
            key("property").isEqualTo(Duration.ofMillis(123))
            key("propertyWithDefaultValue").isEqualTo(5)
            key("mayBeProperty").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isEqualTo("No value")
        }

        assertThat(injectedIntoScenarioOfObject).all {
            key("classToInject").isSameAs(classToInject)
            key("mayBeOtherClassToInject").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isSameAs(otherClassToInject)
            key("injectables").isSameAs(injectablesAsList)
            key("namedInjectables").isSameAs(namedInjectablesAsList)
            key("property").isEqualTo(Duration.ofMillis(123))
            key("propertyWithDefaultValue").isEqualTo(5)
            key("mayBeProperty").isNotNull().isInstanceOf(Optional::class.java).transform { it.get() }
                .isEqualTo("No value")
        }
    }

    /**
     * Loads the scenarios and inject the relevant resources.
     */
    private fun createScenariosLoader(): Collection<ScenarioLoader> {
        val scenariosUrl = this.javaClass.classLoader.getResource("META-INF/services/qalipsis/scenarios")!!
        val scenariosDir = File(scenariosUrl.toURI())
        return scenariosDir.listFiles { file -> file.name.endsWith(".json") }!!
            .map { file ->
                val loaderClass = file.name.removeSuffix(".json")
                Class.forName(loaderClass).getConstructor().newInstance() as ScenarioLoader
            }
    }
}
