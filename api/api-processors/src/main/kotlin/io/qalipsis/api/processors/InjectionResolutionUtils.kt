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
