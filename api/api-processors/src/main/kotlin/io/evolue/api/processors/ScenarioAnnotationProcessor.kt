package io.evolue.api.processors

import io.evolue.api.annotations.Scenario
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ExecutableType
import javax.tools.Diagnostic
import javax.tools.StandardLocation


/**
 *
 * Processor to register the methods creating scenario specifications, in order to load them at startup.
 *
 * @author Eric Jessé
 */

@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedAnnotationTypes(ScenarioAnnotationProcessor.ANNOTATION_CLASS_NAME)
//@SupportedOptions(ScenarioAnnotationProcessor.KAPT_KOTLIN_GENERATED_OPTION_NAME)
internal class ScenarioAnnotationProcessor : AbstractProcessor() {

    companion object {

        const val SCENARIOS_PATH = "META-INF/evolue/scenarios"

        const val ANNOTATION_CLASS_NAME = "io.evolue.api.annotations.Scenario"

        // Property pointing to the folder where Kapt generates sources.
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
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
            emptySet<String>()
        }

        val allScenarios = mutableSetOf<String>()
        allScenarios.addAll(oldScenarios)

        val newScenarios = annotatedElements
            .filter { it.kind == ElementKind.METHOD }
            .map { method ->
                val className = processingEnv.elementUtils.getBinaryName((method.enclosingElement as TypeElement))
                val fullName = "${className}.${method.simpleName}"
                val executableType = method.asType() as ExecutableType;
                if (executableType.parameterTypes.isNotEmpty()) {
                    processingEnv.messager.printMessage(Diagnostic.Kind.ERROR,
                        "Function ${fullName} should have no parameter to declare a scenario")
                    return false
                }
                val executableMethod = ExecutableScenarioMethod(method, processingEnv)
                executableMethod.loaderFullClassName to executableMethod
            }
            .toMap()

        if (!allScenarios.containsAll(newScenarios.keys)) {
            allScenarios.addAll(newScenarios.keys)

            // Create the
            val reallyNewScenarios = mutableSetOf<String>()
            reallyNewScenarios.addAll(newScenarios.keys)
            reallyNewScenarios.removeAll(oldScenarios)

            reallyNewScenarios
                .map { newScenarios[it]!! }
                .forEach { executableMethod ->
                    // Create the source to execute the method.
                    try {
                        val loader =
                            filer.createSourceFile("${executableMethod.loaderFullClassName}")
                        val scenarioCall =
                            when {
                                Modifier.STATIC in executableMethod.scenarioMethod.modifiers -> {
                                    "${executableMethod.scenarioClass.qualifiedName}.${executableMethod.scenarioMethod.simpleName}()"
                                }
                                isAKotlinObject(executableMethod.scenarioClass) -> {
                                    // Kotlin objects.
                                    "${executableMethod.scenarioClass.qualifiedName}.INSTANCE.${executableMethod.scenarioMethod.simpleName}()"
                                }
                                else -> {
                                    // Normal class.
                                    "new ${executableMethod.scenarioClass.qualifiedName}().${executableMethod.scenarioMethod.simpleName}()"
                                }
                            }

                        val sourceCode = """
                            package io.evolue.api.scenariosloader;
                            public class ${executableMethod.loaderClassName}{
                            
                            public ${executableMethod.loaderClassName}(){
                                ${scenarioCall};
                            }
                            
                            }
                        """.trimIndent()

                        OutputStreamWriter(loader.openOutputStream(), StandardCharsets.UTF_8).use { writer ->
                            writer.write(sourceCode)
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

    private fun isAKotlinObject(typeElement: TypeElement) =
        processingEnv.elementUtils.getAllMembers(typeElement)
            .any { it.kind == ElementKind.FIELD && it.simpleName.toString() == "INSTANCE" }

    private data class ExecutableScenarioMethod(
        val scenarioMethod: Element,
        private val processingEnv: ProcessingEnvironment
    ) {
        val scenarioClass = this.scenarioMethod.enclosingElement as TypeElement
        val fullName: String = "${scenarioClass.qualifiedName}.${this.scenarioMethod.simpleName}"
        val loaderUuid: String = "${UUID.nameUUIDFromBytes(this.fullName.toByteArray())}".replace("-", "")
        val loaderClassName: String = "ScenarioLoader${this.loaderUuid}"
        val loaderFullClassName: String = "io.evolue.api.scenariosloader.ScenarioLoader${this.loaderUuid}"
    }
}