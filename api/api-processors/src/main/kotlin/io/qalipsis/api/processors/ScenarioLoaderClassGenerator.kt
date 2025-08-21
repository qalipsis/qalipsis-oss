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

package io.qalipsis.api.processors

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeSpec
import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import jakarta.inject.Named
import java.time.Instant
import javax.annotation.processing.Messager
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind.ERROR
import javax.validation.constraints.NotNull

/**
 * Facility class to generate a [TypeSpec] representing a class with the scenario metadata and supplier.
 *
 * @author Eric Jessé
 */
internal class ScenarioLoaderClassGenerator(
    private val executableMethod: ExecutableScenarioMethod,
    private val messager: Messager,
    private val typeUtils: TypeUtils,
    private val injectionResolutionUtils: InjectionResolutionUtils
) {

    lateinit var scenarioName: String

    lateinit var scenarioDescription: String

    lateinit var scenarioVersion: String

    /**
     * Generates the actual [TypeSpec] used to describe the scenario and load it.
     *
     * @param executableMethod [ExecutableScenarioMethod] describing the function annotated with [Scenario].
     */
    fun generateClassLoader(): TypeSpec {
        val scenarioLoadingStatement =
            buildScenarioLoadingStatement(executableMethod)

        val scenarioLoaderSupplier = MethodSpec.methodBuilder("load")
            .addAnnotation(Override::class.java)
            .addAnnotation(NotNull::class.java)
            .addModifiers(PUBLIC)
            .addParameter(ParameterSpec.builder(INJECTOR_TYPE, "injector").build())
            .addStatement(scenarioLoadingStatement)
            .build()

        val annotation = executableMethod.scenarioMethod.getAnnotation(Scenario::class.java)
        val qualifiedMethodName =
            "${executableMethod.loaderFullClassName}.${executableMethod.scenarioMethod.simpleName}"
        return TypeSpec.classBuilder(executableMethod.loaderClassName)
            .addSuperinterface(SCENARIO_LOADER_TYPE)
            .addModifiers(PUBLIC)
            .overrideMetadataFunctions(annotation, qualifiedMethodName)
            .addMethod(scenarioLoaderSupplier)
            .build()
    }

    /**
     * Generates the statement to execute the function annotated with [Scenario], depending on how
     * it is enclosed (class, Kotlin object or file).
     */
    private fun buildScenarioLoadingStatement(executableMethod: ExecutableScenarioMethod): String {
        return when {
            STATIC in executableMethod.scenarioMethod.modifiers -> {
                """
${executableMethod.scenarioClass.qualifiedName}
    .${executableMethod.scenarioMethod.simpleName}(${addParameters(executableMethod.scenarioMethod)})
                                    """.trimIndent()
            }

            typeUtils.isAKotlinObject(executableMethod.scenarioClass) -> {
                // Kotlin objects.
                """
${executableMethod.scenarioClass.qualifiedName}.INSTANCE
    .${executableMethod.scenarioMethod.simpleName}(${addParameters(executableMethod.scenarioMethod)})
                                    """.trimIndent()
            }

            else -> {
                // Normal class.
                """
new ${executableMethod.scenarioClass.qualifiedName}(${addConstructorParameters(executableMethod.scenarioClass)})
    .${executableMethod.scenarioMethod.simpleName}(${addParameters(executableMethod.scenarioMethod)})
                                    """.trimIndent()
            }
        }
    }

    /**
     * Extracts the parameters for the constructor and generates the injection of related bean.
     */
    private fun addConstructorParameters(type: TypeElement): String {
        return (type.enclosedElements.firstOrNull { it.kind == ElementKind.CONSTRUCTOR } as ExecutableElement?)?.let {
            addParameters(it)
        } ?: ""
    }

    /**
     * Extracts the parameters for a method and generates the injection of related bean.
     */
    private fun addParameters(method: ExecutableElement): String {
        return method.parameters.joinToString(",\n\t\t", prefix = "\n\t\t", postfix = "\n") { param ->
            val propertyAnnotations = param.getAnnotationsByType(Property::class.java)
            val namedAnnotations = param.getAnnotationsByType(Named::class.java)
            val paramType = param.asType()
            when {
                propertyAnnotations.isNotEmpty() -> {
                    injectionResolutionUtils.buildPropertyResolution(propertyAnnotations.first(), paramType)
                }

                namedAnnotations.isNotEmpty() -> {
                    injectionResolutionUtils.buildNamedQualifierResolution(namedAnnotations.first(), paramType)
                }

                else -> injectionResolutionUtils.buildUnqualifiedResolution(paramType)
            }
        }
    }

    /**
     * Creates all the functions to provide metadata of the scenario.
     */
    private fun TypeSpec.Builder.overrideMetadataFunctions(
        scenario: Scenario,
        qualifiedMethodName: String
    ): TypeSpec.Builder {
        scenarioName = scenario.name.trim()
        if (scenarioName.isBlank()) {
            messager.printMessage(
                ERROR,
                "The scenario name on the function $qualifiedMethodName is empty"
            )
        }
        if (!scenarioName.matches(SCENARIO_NAME_VALIDATION_REGEX)) {
            messager.printMessage(
                ERROR,
                "The scenario name '$scenarioName' on the function $qualifiedMethodName is not a valid kebab-case string"
            )
        }
        val description = scenario.description.trim().takeIf(String::isNotBlank)
        scenarioDescription = description ?: ""
        scenarioVersion =
            scenario.version.takeIf(String::isNotBlank) ?: createVersion()

        return this.overrideNameProvider(scenarioName)
            .overrideDescriptionProvider(description)
            .overrideVersionProvider(scenarioVersion)
            .overrideBuildAtProvider()
    }

    private fun createVersion() = "0.${(System.currentTimeMillis() / 1000) - QALIPSIS_EPOCH}"

    private fun TypeSpec.Builder.overrideNameProvider(scenarioName: String): TypeSpec.Builder {
        val method = MethodSpec.methodBuilder("getName")
            .addAnnotation(Override::class.java)
            .addAnnotation(NotNull::class.java)
            .addModifiers(PUBLIC)
            .addStatement("""return "$scenarioName"""")
            .returns(ClassName.get(String::class.java))
            .build()
        return this.addMethod(method)
    }

    private fun TypeSpec.Builder.overrideDescriptionProvider(description: String?): TypeSpec.Builder {
        val actualDescription = description?.let { """"$it"""" } ?: "null"
        val method = MethodSpec.methodBuilder("getDescription")
            .addAnnotation(Override::class.java)
            .addAnnotation(NotNull::class.java)
            .addModifiers(PUBLIC)
            .addStatement("""return $actualDescription""")
            .returns(ClassName.get(String::class.java))
            .build()
        return this.addMethod(method)
    }

    private fun TypeSpec.Builder.overrideVersionProvider(version: String): TypeSpec.Builder {
        val method = MethodSpec.methodBuilder("getVersion")
            .addAnnotation(Override::class.java)
            .addAnnotation(NotNull::class.java)
            .addModifiers(PUBLIC)
            .addStatement("""return "$version"""")
            .returns(ClassName.get(String::class.java))
            .build()
        return this.addMethod(method)
    }

    private fun TypeSpec.Builder.overrideBuildAtProvider(): TypeSpec.Builder {
        val buildTime = Instant.now()
        val method = MethodSpec.methodBuilder("getBuiltAt")
            .addAnnotation(Override::class.java)
            .addAnnotation(NotNull::class.java)
            .addModifiers(PUBLIC)
            .addStatement("""return Instant.parse("$buildTime")""")
            .returns(ClassName.get(Instant::class.java))
            .build()
        return this.addMethod(method)
    }

    private companion object {

        /**
         * EPOCH for QALIPSIS, to create new version.
         */
        const val QALIPSIS_EPOCH = 1704067200

        /**
         * Validation rule for kebab-case scenario names.
         */
        val SCENARIO_NAME_VALIDATION_REGEX = Regex("^[-a-z0-9-]+$")

        /**
         * Supertype of injector for properties and dependencies.
         */
        val INJECTOR_TYPE = ClassName.bestGuess("io.qalipsis.api.scenario.Injector")

        /**
         * Supertype of class to load a scenario.
         */
        val SCENARIO_LOADER_TYPE = ClassName.bestGuess("io.qalipsis.api.scenario.ScenarioLoader")
    }
}