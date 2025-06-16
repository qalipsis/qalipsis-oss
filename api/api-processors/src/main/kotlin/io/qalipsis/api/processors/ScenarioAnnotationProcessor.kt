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

import com.squareup.javapoet.JavaFile
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.services.ServicesFiles
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind.METHOD
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.StandardLocation


/**
 * Processor to register the methods creating scenario specifications, in order to load them at startup.
 * This processor creates implementations of [io.qalipsis.api.injector.Injector] containing the declaration
 * of the scenario and metadata.
 *
 * If the enclosing class or the method itself expects parameters, they are injected from the Micronaut runtime.
 *
 * @author Eric Jessé
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedAnnotationTypes(ScenarioAnnotationProcessor.ANNOTATION_CLASS_NAME)
internal class ScenarioAnnotationProcessor : AbstractProcessor() {

    companion object {

        const val SCENARIOS_PATH = "META-INF/services/qalipsis/scenarios"

        const val ANNOTATION_CLASS_NAME = "io.qalipsis.api.annotations.Scenario"

        const val RESOURCE_FIELD_SEPARATOR = "\t"
    }

    private lateinit var typeUtils: TypeUtils

    private lateinit var injectionResolutionUtils: InjectionResolutionUtils

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        typeUtils = TypeUtils(processingEnv.elementUtils, processingEnv.typeUtils)
        injectionResolutionUtils = InjectionResolutionUtils(typeUtils)
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {

        val annotatedElements = roundEnv.getElementsAnnotatedWith(Scenario::class.java)
        if (annotatedElements.isEmpty()) return false

        val filer = processingEnv.filer
        // Loading the existing file.
        val existingFile = filer.getResource(StandardLocation.CLASS_OUTPUT, "", SCENARIOS_PATH)
        // Looking for existing resources.
        val alreadyProcessedScenarios = try {
            ServicesFiles.readFile(existingFile.openInputStream())
        } catch (e: IOException) {
            emptySet()
        }.map { line ->
            ScenarioResourceMetadata.ofLine(line)
        }.associateBy { it.scenarioName }
        val allScenariosByName = alreadyProcessedScenarios.toMutableMap()

        annotatedElements
            .asSequence()
            .filter { it.kind == METHOD }
            .map { method ->
                ExecutableScenarioMethod(method as ExecutableElement)
            }
            .forEach { executableMethod ->
                val scenarioLoaderClassGenerator = ScenarioLoaderClassGenerator(
                    executableMethod,
                    processingEnv.messager,
                    typeUtils,
                    injectionResolutionUtils
                )
                val loaderSpec = scenarioLoaderClassGenerator.generateClassLoader()

                if (allScenariosByName.containsKey(scenarioLoaderClassGenerator.scenarioName)) {
                    processingEnv.messager.printMessage(
                        ERROR,
                        "The scenario with name ${scenarioLoaderClassGenerator.scenarioName} already exists in the source files"
                    )
                }
                allScenariosByName[scenarioLoaderClassGenerator.scenarioName] = ScenarioResourceMetadata(
                    scenarioName = scenarioLoaderClassGenerator.scenarioName,
                    scenarioDescription = scenarioLoaderClassGenerator.scenarioDescription,
                    scenarioVersion = scenarioLoaderClassGenerator.scenarioVersion,
                    scenarioLoader = executableMethod.loaderFullClassName
                )

                val javaFile = JavaFile.builder("io.qalipsis.api.scenariosloader", loaderSpec).build()
                val loader = filer.createSourceFile("${executableMethod.loaderFullClassName}")
                OutputStreamWriter(loader.openOutputStream(), StandardCharsets.UTF_8).use { writer ->
                    javaFile.writeTo(writer)
                    writer.flush()
                }
            }

        // Create the list of scenarios as a resource.
        val scenariosFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", SCENARIOS_PATH)
        ServicesFiles.writeFile(allScenariosByName.map { it.value.asResource }, scenariosFile.openOutputStream())

        return true
    }

    /**
     * Represents the metadata of a scenario to write into the resource file.
     */
    private data class ScenarioResourceMetadata(
        val scenarioName: String,
        val scenarioDescription: String,
        val scenarioVersion: String,
        val scenarioLoader: String
    ) {
        val asResource: String =
            "$scenarioName$RESOURCE_FIELD_SEPARATOR$scenarioDescription$RESOURCE_FIELD_SEPARATOR$scenarioVersion$RESOURCE_FIELD_SEPARATOR$scenarioLoader"

        companion object {

            fun ofLine(line: String): ScenarioResourceMetadata {
                val tokens = line.split(RESOURCE_FIELD_SEPARATOR)
                return ScenarioResourceMetadata(
                    scenarioName = tokens[0],
                    scenarioDescription = tokens[1],
                    scenarioVersion = tokens[2],
                    scenarioLoader = tokens[3]
                )
            }

        }
    }

}
