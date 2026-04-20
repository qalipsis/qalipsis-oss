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
import io.qalipsis.api.processors.ScenarioAnnotationProcessor.Companion.SCENARIOS_DIR
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
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
 * Each scenario gets its own JSON metadata file under [SCENARIOS_DIR], named after the loader class.
 * This enables incremental builds since only the changed scenario's file is regenerated.
 *
 * If the enclosing class or the method itself expects parameters, they are injected from the Micronaut runtime.
 *
 * @author Eric Jessé
 */
@SupportedAnnotationTypes(ScenarioAnnotationProcessor.ANNOTATION_CLASS_NAME)
internal class ScenarioAnnotationProcessor : AbstractProcessor() {

    companion object {

        const val SCENARIOS_DIR = "META-INF/services/qalipsis/scenarios"

        const val ANNOTATION_CLASS_NAME = "io.qalipsis.api.annotations.Scenario"
    }

    private lateinit var typeUtils: TypeUtils

    private lateinit var injectionResolutionUtils: InjectionResolutionUtils

    override fun getSupportedSourceVersion(): SourceVersion? {
        return SourceVersion.latestSupported()
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        typeUtils = TypeUtils(processingEnv.elementUtils, processingEnv.typeUtils)
        injectionResolutionUtils = InjectionResolutionUtils(typeUtils)
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {

        val annotatedElements = roundEnv.getElementsAnnotatedWith(Scenario::class.java)
        if (annotatedElements.isEmpty()) return false

        val filer = processingEnv.filer
        val processedNames = mutableSetOf<String>()

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

                if (!processedNames.add(scenarioLoaderClassGenerator.scenarioName)) {
                    processingEnv.messager.printMessage(
                        ERROR,
                        "The scenario with name ${scenarioLoaderClassGenerator.scenarioName} already exists in the source files"
                    )
                }

                val javaFile = JavaFile.builder("io.qalipsis.api.scenariosloader", loaderSpec).build()
                val loader = filer.createSourceFile("${executableMethod.loaderFullClassName}")
                OutputStreamWriter(loader.openOutputStream(), StandardCharsets.UTF_8).use { writer ->
                    javaFile.writeTo(writer)
                    writer.flush()
                }

                // Write the individual JSON metadata file, named after the loader class.
                val scenarioFile = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "",
                    "$SCENARIOS_DIR/${executableMethod.loaderFullClassName}.json"
                )
                OutputStreamWriter(scenarioFile.openOutputStream(), StandardCharsets.UTF_8).use { writer ->
                    writer.write(
                        buildJsonMetadata(
                            scenarioLoaderClassGenerator.scenarioName,
                            scenarioLoaderClassGenerator.scenarioDescription,
                            scenarioLoaderClassGenerator.scenarioVersion,
                            executableMethod.loaderFullClassName
                        )
                    )
                    writer.flush()
                }
            }

        return true
    }

    private fun buildJsonMetadata(name: String, description: String, version: String, loader: String): String {
        return """
            |{
            |  "name": "${escapeJson(name)}",
            |  "description": "${escapeJson(description)}",
            |  "version": "${escapeJson(version)}",
            |  "loader": "${escapeJson(loader)}"
            |}
        """.trimMargin()
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

}
