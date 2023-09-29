/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.processors.scenario

import assertk.all
import assertk.assertThat
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
import io.qalipsis.api.services.ServicesFiles
import io.qalipsis.test.io.readFileLines
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
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
        val scenarioResources = this.javaClass.classLoader.getResources("META-INF/services/qalipsis/scenarios").toList()

        Assertions.assertEquals(1, scenarioResources.size)
        val scenarios = readFileLines(scenarioResources[0].openStream(), true, "#").toSet()

        val expected = setOf(
            "a-method-outside-a-class\tScenario in a file\t0.1\tio.qalipsis.api.scenariosloader.ScenarioClassKt\$\$aMethodOutsideAClass",
            "a-method-inside-an-object\t\t1.34\tio.qalipsis.api.scenariosloader.ScenarioDeclaration\$\$aMethodInsideAnObject",
            "a-method-inside-a-class\tScenario in a class\t0.2.3\tio.qalipsis.api.scenariosloader.ScenarioClass\$\$aMethodInsideAClass"
        )
        Assertions.assertEquals(expected, scenarios)
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
        return this.javaClass.classLoader.getResources("META-INF/services/qalipsis/scenarios")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { scenarioMetadata ->
                val loaderClass = scenarioMetadata.split("\t")[3]
                Class.forName(loaderClass).getConstructor().newInstance() as ScenarioLoader
            }
    }
}
