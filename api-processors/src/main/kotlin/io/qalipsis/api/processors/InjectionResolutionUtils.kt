package io.qalipsis.api.processors

import io.qalipsis.api.annotations.Property
import jakarta.inject.Named
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeMirror

/**
 * Utility class to find out the type of a type.
 *
 * @author Eric Jessé
 */
internal class InjectionResolutionUtils(private val typeUtils: TypeUtils) {

    fun buildPropertyResolution(property: Property, paramType: TypeMirror): String {
        var isOptional = false
        val propertyTypeName = if (typeUtils.isOptionalWithGeneric(paramType)) {
            isOptional = true
            "${typeUtils.getTypeOfFirstGeneric(paramType as DeclaredType)}.class"
        } else if (paramType is PrimitiveType) {
            PRIMITIVES_TYPES[paramType.toString()]
        } else {
            "${typeUtils.erase(paramType)}.class"
        }
        return if (property.orElse.isBlank() || isOptional) {
            if (isOptional) {
                "($paramType) injector.getProperty(\"${property.name}\", ${propertyTypeName})"
            } else {
                "injector.getRequiredProperty(\"${property.name}\", ${propertyTypeName})"
            }
        } else {
            "injector.getProperty(\"${property.name}\", ${propertyTypeName}, ($paramType) injector.getConversionService().convertRequired(\"${property.orElse}\", $propertyTypeName))"
        }
    }

    fun buildUnqualifiedResolution(paramType: TypeMirror): String {
        return when {
            typeUtils.isOptionalWithGeneric(paramType) -> {
                val genericType = typeUtils.getTypeOfFirstGeneric(paramType as DeclaredType)
                """injector.findBean(${genericType}.class)"""
            }
            typeUtils.isIterableWithGeneric(paramType) -> {
                val genericType = typeUtils.getTypeOfFirstGeneric(paramType as DeclaredType)
                """($paramType) injector.getConversionService().convertRequired(injector.getBeansOfType(${genericType}.class), 
                    ${typeUtils.erase(paramType)}.class)""".trimIndent()
            }
            else -> {
                """injector.getBean(${paramType}.class)"""
            }
        }
    }

    fun buildNamedQualifierResolution(named: Named, paramType: TypeMirror): String {
        return when {
            typeUtils.isOptionalWithGeneric(paramType) -> {
                val genericType = typeUtils.getTypeOfFirstGeneric(paramType as DeclaredType)
                """injector.findBean(${genericType}.class, io.micronaut.inject.qualifiers.Qualifiers.byName("${named.value}"))"""
            }
            typeUtils.isIterableWithGeneric(paramType) -> {
                val genericType = typeUtils.getTypeOfFirstGeneric(paramType as DeclaredType)
                """($paramType) injector.getConversionService().convertRequired(injector.getBeansOfType(${genericType}.class, io.micronaut.inject.qualifiers.Qualifiers.byName("${named.value}")), ${
                    typeUtils.erase(
                        paramType
                    )
                }.class)"""
            }
            else -> {
                """injector.getBean(${paramType}.class, io.micronaut.inject.qualifiers.Qualifiers.byName("${named.value}"))"""
            }
        }
    }

    companion object {

        @JvmStatic
        private val PRIMITIVES_TYPES = mapOf(
            "boolean" to "java.lang.Boolean.TYPE",
            "byte" to "java.lang.Byte.TYPE",
            "short" to "java.lang.Short.TYPE",
            "int" to "java.lang.Integer.TYPE",
            "long" to "java.lang.Long.TYPE",
            "char" to "java.lang.Character.TYPE",
            "float" to "java.lang.Float.TYPE",
            "double" to "java.lang.Double.TYPE"
        )
    }
}
