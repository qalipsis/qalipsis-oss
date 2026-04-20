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

package io.qalipsis.api.processors.configuration

import io.qalipsis.api.processors.configuration.ConfigurationReferenceAnnotationProcessor.Companion.OPTION_OUTPUT_DIR
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.tools.Diagnostic

/**
 * Annotation processor that extracts configuration property references from Micronaut annotations
 * and generates AsciiDoc documentation tables.
 *
 * Processes the following annotations:
 * - `@ConfigurationProperties` — class-level prefix with `var` field properties
 * - `@EachProperty` — same as above but with `*` wildcard in the path
 * - `@Property` — constructor/method parameter with explicit name and default
 * - `@Value` — constructor/method parameter with `${path:default}` expressions
 *
 * Disabled by default: if [OPTION_OUTPUT_DIR] is not provided, [process] returns immediately.
 *
 * @author Eric Jessé
 */
@SupportedAnnotationTypes(
    ConfigurationReferenceAnnotationProcessor.CONFIGURATION_PROPERTIES,
    ConfigurationReferenceAnnotationProcessor.EACH_PROPERTY,
    ConfigurationReferenceAnnotationProcessor.PROPERTY,
    ConfigurationReferenceAnnotationProcessor.VALUE
)
@SupportedOptions(
    ConfigurationReferenceAnnotationProcessor.OPTION_OUTPUT_DIR,
    ConfigurationReferenceAnnotationProcessor.OPTION_RESOURCES_DIR
)
internal class ConfigurationReferenceAnnotationProcessor : AbstractProcessor() {

    companion object {
        const val CONFIGURATION_PROPERTIES = "io.micronaut.context.annotation.ConfigurationProperties"
        const val EACH_PROPERTY = "io.micronaut.context.annotation.EachProperty"
        const val PROPERTY = "io.micronaut.context.annotation.Property"
        const val VALUE = "io.micronaut.context.annotation.Value"

        const val OPTION_OUTPUT_DIR = "configuration.reference.output.dir"
        const val OPTION_RESOURCES_DIR = "configuration.reference.resources.dir"

        private const val BINDABLE = "io.micronaut.core.bind.annotation.Bindable"

        private val SKIPPED_GETTER_NAMES = setOf("class", "string", "hashCode")

        private val CAMEL_CASE_REGEX = Regex("([a-z0-9])([A-Z])")

        /**
         * Converts a camelCase name to kebab-case.
         */
        fun toKebabCase(name: String): String {
            return CAMEL_CASE_REGEX.replace(name) { "${it.groupValues[1]}-${it.groupValues[2]}" }.lowercase()
        }
    }

    private val allProperties = mutableMapOf<String, ConfigurationProperty>()

    private val overrideReader = ConfigurationOverrideReader()

    private val tableGenerator = AsciidocTableGenerator()

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val outputDir = processingEnv.options[OPTION_OUTPUT_DIR] ?: return false

        if (!roundEnv.processingOver()) {
            processConfigurationPropertiesClasses(roundEnv, CONFIGURATION_PROPERTIES, wildcard = false)
            processConfigurationPropertiesClasses(roundEnv, EACH_PROPERTY, wildcard = true)
            processPropertyAnnotations(roundEnv)
            processValueAnnotations(roundEnv)
        } else {
            if (allProperties.isEmpty()) return false

            val resourcesDir = processingEnv.options[OPTION_RESOURCES_DIR] ?: ""
            val overrides = overrideReader.read(resourcesDir)

            val finalProperties = applyOverrides(allProperties.values.toList(), overrides.properties)
            if (finalProperties.isEmpty()) return false

            val output = tableGenerator.generate(finalProperties, overrides.groups)
            val dir = File(outputDir)
            dir.mkdirs()
            File(dir, "configuration-reference.adoc").writeText(output)

            processingEnv.messager.printMessage(
                Diagnostic.Kind.NOTE,
                "Generated configuration reference with ${finalProperties.size} properties in $outputDir"
            )
            allProperties.clear()
        }
        return false
    }

    /**
     * Processes classes annotated with `@ConfigurationProperties` or `@EachProperty`.
     */
    private fun processConfigurationPropertiesClasses(
        roundEnv: RoundEnvironment,
        annotationName: String,
        wildcard: Boolean
    ) {
        val annotationType = processingEnv.elementUtils.getTypeElement(annotationName) ?: return
        for (element in roundEnv.getElementsAnnotatedWith(annotationType)) {
            if (element.kind != ElementKind.CLASS) continue
            val typeElement = element as TypeElement
            val prefix = buildPrefixChain(typeElement, wildcard)
            extractFieldProperties(typeElement, prefix)
        }
    }

    /**
     * Builds the full prefix chain by walking enclosing classes annotated with
     * `@ConfigurationProperties` or `@EachProperty`.
     */
    private fun buildPrefixChain(typeElement: TypeElement, wildcard: Boolean): String {
        val segments = mutableListOf<String>()

        // Get the annotation value for this class.
        val localPrefix = getAnnotationValue(typeElement)
        if (localPrefix.isNotEmpty()) {
            if (wildcard) {
                segments.add(localPrefix)
                segments.add("*")
            } else {
                segments.add(localPrefix)
            }
        }

        // Walk enclosing classes upward to build the full prefix.
        var enclosing = typeElement.enclosingElement
        while (enclosing != null && enclosing.kind == ElementKind.CLASS) {
            val enclosingType = enclosing as TypeElement
            val enclosingIsEach = hasAnnotation(enclosingType, EACH_PROPERTY)
            val enclosingPrefix = getAnnotationValue(enclosingType)
            if (enclosingPrefix.isNotEmpty()) {
                if (enclosingIsEach) {
                    segments.add(0, "*")
                    segments.add(0, enclosingPrefix)
                } else {
                    segments.add(0, enclosingPrefix)
                }
            }
            enclosing = enclosingType.enclosingElement
        }

        return segments.joinToString(".")
    }

    /**
     * Extracts the `value` attribute from `@ConfigurationProperties` or `@EachProperty` on the given element.
     */
    private fun getAnnotationValue(element: TypeElement): String {
        for (mirror in element.annotationMirrors) {
            val annotationName = mirror.annotationType.toString()
            if (annotationName == CONFIGURATION_PROPERTIES || annotationName == EACH_PROPERTY) {
                for ((key, value) in mirror.elementValues) {
                    if (key.simpleName.toString() == "value") {
                        return value.value.toString()
                    }
                }
            }
        }
        return ""
    }

    /**
     * Checks whether the given element has a specific annotation.
     */
    private fun hasAnnotation(element: Element, annotationName: String): Boolean {
        return element.annotationMirrors.any { it.annotationType.toString() == annotationName }
    }

    /**
     * Extracts `var` fields (getter-based properties) from a `@ConfigurationProperties`/`@EachProperty` class.
     * Prefers getter methods over fields. Skips companion object constants and nested config classes.
     */
    private fun extractFieldProperties(typeElement: TypeElement, prefix: String) {
        val processedPaths = mutableSetOf<String>()

        // First pass: collect from getter methods (preferred — they carry doc comments from KAPT stubs).
        for (enclosed in typeElement.enclosedElements) {
            if (enclosed.kind != ElementKind.METHOD) continue
            val method = enclosed as ExecutableElement
            val methodName = method.simpleName.toString()

            val fieldName = when {
                methodName.startsWith("get") && methodName.length > 3 ->
                    methodName.substring(3).replaceFirstChar { it.lowercase() }

                methodName.startsWith("is") && methodName.length > 2 ->
                    methodName.substring(2).replaceFirstChar { it.lowercase() }

                else -> null
            } ?: continue

            // Skip toString, hashCode, getClass, etc.
            if (fieldName in SKIPPED_GETTER_NAMES) continue

            // Skip methods that return nested @ConfigurationProperties classes.
            val returnType = method.returnType
            if (returnType.kind == TypeKind.DECLARED) {
                val returnElement = (returnType as DeclaredType).asElement()
                if (hasAnnotation(returnElement, CONFIGURATION_PROPERTIES) ||
                    hasAnnotation(returnElement, EACH_PROPERTY)
                ) {
                    continue
                }
            }

            val kebabName = toKebabCase(fieldName)
            val path = if (prefix.isEmpty()) kebabName else "$prefix.$kebabName"
            if (!processedPaths.add(path)) continue

            val typeName = simplifyTypeName(method.returnType.toString())
            val defaultValue = extractBindableDefault(method)
            val description = cleanDocComment(processingEnv.elementUtils.getDocComment(method))
                ?: cleanDocComment(findFieldDocComment(typeElement, fieldName))

            allProperties.putIfAbsent(
                path,
                ConfigurationProperty(
                    path = path,
                    type = typeName,
                    defaultValue = defaultValue,
                    description = description
                )
            )
        }

        // Second pass: collect from fields not already covered by getters.
        for (enclosed in typeElement.enclosedElements) {
            if (enclosed.kind != ElementKind.FIELD) continue
            val field = enclosed as VariableElement
            val fieldName = field.simpleName.toString()

            // Skip synthetic, companion, or static-like fields and constants.
            if (fieldName.startsWith("$") || fieldName == "Companion") continue
            if (field.constantValue != null) continue

            // Skip fields whose type is a nested @ConfigurationProperties class.
            val fieldType = field.asType()
            if (fieldType.kind == TypeKind.DECLARED) {
                val fieldTypeElement = (fieldType as DeclaredType).asElement()
                if (hasAnnotation(fieldTypeElement, CONFIGURATION_PROPERTIES) ||
                    hasAnnotation(fieldTypeElement, EACH_PROPERTY)
                ) {
                    continue
                }
            }

            val kebabName = toKebabCase(fieldName)
            val path = if (prefix.isEmpty()) kebabName else "$prefix.$kebabName"
            if (!processedPaths.add(path)) continue

            val typeName = simplifyTypeName(field.asType().toString())
            val description = cleanDocComment(processingEnv.elementUtils.getDocComment(field))

            allProperties.putIfAbsent(
                path,
                ConfigurationProperty(
                    path = path,
                    type = typeName,
                    defaultValue = null,
                    description = description
                )
            )
        }
    }

    /**
     * Tries to find a doc comment on the field matching a getter method.
     */
    private fun findFieldDocComment(typeElement: TypeElement, fieldName: String): String? {
        return typeElement.enclosedElements
            .firstOrNull { it.kind == ElementKind.FIELD && it.simpleName.toString() == fieldName }
            ?.let { processingEnv.elementUtils.getDocComment(it) }
    }

    /**
     * Extracts the `defaultValue` from a `@Bindable` annotation on a method.
     */
    private fun extractBindableDefault(method: ExecutableElement): String? {
        for (mirror in method.annotationMirrors) {
            if (mirror.annotationType.toString() == BINDABLE) {
                for ((key, value) in mirror.elementValues) {
                    if (key.simpleName.toString() == "defaultValue") {
                        val str = value.value.toString()
                        return str.ifEmpty { null }
                    }
                }
            }
        }
        return null
    }

    /**
     * Processes `@Property` annotations on constructor and method parameters.
     */
    private fun processPropertyAnnotations(roundEnv: RoundEnvironment) {
        val annotationType = processingEnv.elementUtils.getTypeElement(PROPERTY) ?: return
        for (element in roundEnv.getElementsAnnotatedWith(annotationType)) {
            val mirror = element.annotationMirrors.firstOrNull { it.annotationType.toString() == PROPERTY } ?: continue
            var name: String? = null
            var defaultValue: String? = null

            for ((key, value) in mirror.elementValues) {
                when (key.simpleName.toString()) {
                    "name" -> name = value.value.toString().ifEmpty { null }
                    "defaultValue" -> defaultValue = value.value.toString().ifEmpty { null }
                }
            }

            if (name != null) {
                val typeName = simplifyTypeName(element.asType().toString())
                val description = cleanDocComment(processingEnv.elementUtils.getDocComment(element))

                allProperties.putIfAbsent(
                    name,
                    ConfigurationProperty(
                        path = name,
                        type = typeName,
                        defaultValue = defaultValue,
                        description = description
                    )
                )
            }
        }
    }

    /**
     * Processes `@Value` annotations on constructor and method parameters.
     * Parses `"${path:default}"` expressions to extract property path and default value.
     */
    private fun processValueAnnotations(roundEnv: RoundEnvironment) {
        val annotationType = processingEnv.elementUtils.getTypeElement(VALUE) ?: return
        for (element in roundEnv.getElementsAnnotatedWith(annotationType)) {
            val mirror = element.annotationMirrors.firstOrNull { it.annotationType.toString() == VALUE } ?: continue
            var expression: String? = null

            for ((key, value) in mirror.elementValues) {
                if (key.simpleName.toString() == "value") {
                    expression = value.value.toString()
                }
            }

            if (expression != null) {
                val (path, defaultValue) = parseValueExpression(expression)
                if (path != null) {
                    val typeName = simplifyTypeName(element.asType().toString())
                    val description = cleanDocComment(processingEnv.elementUtils.getDocComment(element))

                    allProperties.putIfAbsent(
                        path,
                        ConfigurationProperty(
                            path = path,
                            type = typeName,
                            defaultValue = defaultValue,
                            description = description
                        )
                    )
                }
            }
        }
    }

    /**
     * Parses a `@Value` expression like `"${path:default}"` into a pair of (path, default).
     */
    private fun parseValueExpression(expression: String): Pair<String?, String?> {
        val match = VALUE_EXPRESSION_REGEX.find(expression) ?: return null to null
        val path = match.groupValues[1]
        val defaultValue = match.groupValues[2].ifEmpty { null }
        return path to defaultValue
    }

    /**
     * Applies overrides: ignore properties, replace descriptions, and add extra entries.
     */
    private fun applyOverrides(
        properties: List<ConfigurationProperty>,
        overrides: Map<String, PropertyOverride>
    ): List<ConfigurationProperty> {
        val result = mutableListOf<ConfigurationProperty>()

        for (prop in properties) {
            val override = overrides[prop.path]
            if (override != null) {
                if (override.ignore) continue
                result.add(
                    prop.copy(
                        description = override.description ?: prop.description,
                        defaultValue = override.default ?: prop.defaultValue,
                        type = override.type ?: prop.type
                    )
                )
            } else {
                result.add(prop)
            }
        }

        // Add extra entries from overrides that are not found in code.
        val existingPaths = result.map { it.path }.toSet()
        for ((path, override) in overrides) {
            if (path !in existingPaths && !override.ignore && override.type != null) {
                result.add(
                    ConfigurationProperty(
                        path = path,
                        type = override.type,
                        defaultValue = override.default,
                        description = override.description
                    )
                )
            }
        }

        return result
    }

    /**
     * Simplifies a fully qualified type name to its simple form.
     * Strips annotation prefixes (e.g. `@Positive int` -> `int`) and package qualifiers.
     */
    private fun simplifyTypeName(typeName: String): String {
        // Strip annotation prefixes like @Positive, @NotEmpty, etc.
        val withoutAnnotations = typeName.replace(Regex("""@[\w.]+ """), "")
        return withoutAnnotations
            .replace(Regex("""[\w.]+\.(\w+)"""), "$1")
    }

    /**
     * Cleans up a doc comment string: strips leading `*`, whitespace, and `@param`/`@author` tags.
     * Returns null if the comment is blank or null.
     */
    private fun cleanDocComment(docComment: String?): String? {
        if (docComment.isNullOrBlank()) return null

        val lines = docComment.lines()
            .map { it.trimStart().removePrefix("*").trimStart() }
            .dropWhile { it.isBlank() }

        val result = StringBuilder()
        for (line in lines) {
            // Stop at tag lines.
            if (line.startsWith("@")) break
            if (line.isBlank() && result.isNotEmpty()) break
            if (result.isNotEmpty()) result.append(" ")
            result.append(line.trim())
        }

        return result.toString().ifBlank { null }
    }
}

private val VALUE_EXPRESSION_REGEX = Regex("""\$\{([^:}]+)(?::([^}]*))?}""")
