package io.qalipsis.api.processors

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeSpec
import io.micronaut.context.ApplicationContext
import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.services.ServicesFiles
import jakarta.inject.Named
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation


/**
 *
 * Processor to register the methods creating scenario specifications, in order to load them at startup.
 *
 * If the enclosing class or the method itself expects parameters, they are injected from the Micronaut runtime.
 *
 * @author Eric Jessé
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedAnnotationTypes(ScenarioAnnotationProcessor.ANNOTATION_CLASS_NAME)
internal class ScenarioAnnotationProcessor : AbstractProcessor() {

    companion object {

        const val SCENARIOS_PATH = "META-INF/qalipsis/scenarios"

        const val ANNOTATION_CLASS_NAME = "io.qalipsis.api.annotations.Scenario"

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
        val oldScenarios = try {
            ServicesFiles.readFile(existingFile.openInputStream())
        } catch (e: IOException) {
            emptySet()
        }

        val allScenarios = mutableSetOf<String>()
        allScenarios.addAll(oldScenarios)

        val newScenarios = annotatedElements
            .filter { it.kind == ElementKind.METHOD }
            .map { method ->
                val executableMethod = ExecutableScenarioMethod(method as ExecutableElement, processingEnv)
                executableMethod.loaderFullClassName to executableMethod
            }
            .toMap()

        if (!allScenarios.containsAll(newScenarios.keys)) {
            allScenarios.addAll(newScenarios.keys)

            val reallyNewScenarios = mutableSetOf<String>()
            reallyNewScenarios.addAll(newScenarios.keys)
            reallyNewScenarios.removeAll(oldScenarios)

            reallyNewScenarios
                .map { newScenarios[it] ?: error("The scenarios for key $it should exist") }
                .forEach { executableMethod ->
                    // Create the source to execute the method with the injection of the relevant runtime beans.
                    try {
                        val scenarioCall =
                            when {
                                Modifier.STATIC in executableMethod.scenarioMethod.modifiers -> {
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

                        val constructor = MethodSpec.constructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(
                                ParameterSpec.builder(
                                    ApplicationContext::class.java, "applicationContext",
                                    Modifier.FINAL
                                ).build()
                            )
                            .addStatement(scenarioCall)
                            .build()

                        val loaderSpec = TypeSpec.classBuilder(executableMethod.loaderClassName)
                            .addModifiers(Modifier.PUBLIC)
                            .addMethod(constructor)
                            .build()

                        val javaFile = JavaFile.builder("io.qalipsis.api.scenariosloader", loaderSpec).build()
                        val loader = filer.createSourceFile("${executableMethod.loaderFullClassName}")
                        OutputStreamWriter(loader.openOutputStream(), StandardCharsets.UTF_8).use { writer ->
                            javaFile.writeTo(writer)
                            writer.flush()
                        }
                    } catch (e: Exception) {
                        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, e.message)
                        return false
                    }
                }

            // Create the list of scenarios.
            val scenariosFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", SCENARIOS_PATH)
            ServicesFiles.writeFile(allScenarios, scenariosFile.openOutputStream())
        }
        return true
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
     * Wrapping class to transport metadata about the scenario to declare.
     */
    private data class ExecutableScenarioMethod(
        val scenarioMethod: ExecutableElement,
        private val processingEnv: ProcessingEnvironment
    ) {
        val scenarioClass = this.scenarioMethod.enclosingElement as TypeElement
        val loaderClassName: String = "${scenarioClass.simpleName}\$${this.scenarioMethod.simpleName}"
        val loaderFullClassName: String = "io.qalipsis.api.scenariosloader.${loaderClassName}"
    }
}
