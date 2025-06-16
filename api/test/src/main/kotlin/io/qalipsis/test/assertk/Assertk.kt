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

package io.qalipsis.test.assertk

import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.prop
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible

/**
 *
 * @author Eric Jessé
 */

/**
 * Create an assertk assert on the hidden property with the given name and type.
 */
@Suppress("UNCHECKED_CAST")
fun <P> Assert<*>.typedProp(name: String): Assert<P> {
    this.isNotNull()
    return (this as Assert<Any>).prop(name) { assertedValue ->
        collectProperties(assertedValue::class)
            .first { it.name == name }.let { property ->
                property.isAccessible = true
                (property as KProperty1<Any, P>).get(assertedValue)
            }
    }
}

/**
 * Create an assertk assert on the hidden property with the given name.
 */
@Suppress("UNCHECKED_CAST")
fun Assert<*>.prop(name: String): Assert<*> {
    this.isNotNull()
    return (this as Assert<Any>).prop(name) { assertedValue ->
        collectProperties(assertedValue::class)
            .first { it.name == name }.let { property ->
                property.isAccessible = true
                (property as KProperty1<Any, *>).get(assertedValue)
            }
    }
}

internal fun collectProperties(instanceClass: KClass<*>): Collection<KProperty1<*, *>> {
    val properties = instanceClass.memberProperties
        .associateBy { it.name }.toMutableMap()
    var parentClasses = instanceClass.superclasses
    while (parentClasses.isNotEmpty()) {
        val nextLevelParentClasses = mutableListOf<KClass<*>>()
        parentClasses.forEach { parentClass ->
            nextLevelParentClasses.addAll(parentClass.superclasses)
            parentClass.memberProperties.forEach { prop ->
                if (!properties.containsKey(prop.name)) {
                    prop.isAccessible = true
                    properties[prop.name] = prop
                }
            }
        }
        parentClasses = nextLevelParentClasses
    }

    return properties.values
}