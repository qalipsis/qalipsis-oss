/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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