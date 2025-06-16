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

import java.util.Optional
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.WildcardType
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * Utility class to find out the type of a type.
 *
 * @author Eric Jessé
 */
internal class TypeUtils(private val elementUtils: Elements, private val typeUtils: Types) {

    private val iterableType = typeUtils.erasure(elementUtils.getTypeElement(Iterable::class.java.name).asType())

    private val optionalType = typeUtils.erasure(elementUtils.getTypeElement(Optional::class.java.name).asType())

    /**
     * Verifies if the element passed as parameter is a Kotlin Object.
     */
    fun isAKotlinObject(typeElement: TypeElement) =
        elementUtils.getAllMembers(typeElement)
            .any { it.kind == ElementKind.FIELD && it.simpleName.toString() == "INSTANCE" }


    fun isIterableWithGeneric(type: TypeMirror): Boolean {
        return if (type is DeclaredType && type.typeArguments.size == 1) {
            return typeUtils.isSubtype(typeUtils.erasure(type), iterableType)
        } else {
            false
        }
    }

    fun isOptionalWithGeneric(type: TypeMirror): Boolean {
        return if (type is DeclaredType && type.typeArguments.size == 1) {
            return typeUtils.isSubtype(typeUtils.erasure(type), optionalType)
        } else {
            false
        }
    }

    fun getTypeOfFirstGeneric(type: DeclaredType): TypeMirror {
        var genericType = type.typeArguments.first()
        if (genericType is WildcardType) {
            genericType = typeUtils.erasure(genericType)
        }
        return genericType
    }

    fun erase(type: TypeMirror): TypeMirror {
        return typeUtils.erasure(type)
    }

    /**
     * Returns the [TypeElement] for the [TypeMirror] passed as parameter if it exists.
     * Primitive types returns null.
     */
    fun getTypeElement(typeMirror: TypeMirror) = typeUtils.asElement(typeMirror) as TypeElement?
}
