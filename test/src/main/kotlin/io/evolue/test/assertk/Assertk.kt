package io.evolue.test.assertk

import assertk.Assert
import assertk.assertions.prop
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 *
 * @author Eric Jessé
 */

/**
 * Create an assertk assert on the property with the given name and type.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any, P> Assert<T>.typedProp(name: String): Assert<P> {
    return prop(name) { assertedValue ->
        assertedValue::class.memberProperties
            .first { it.name == name }.let { property ->
                property.isAccessible = true
                (property as KProperty1<T, P>).get(assertedValue)
            }
    }
}

/**
 * Create an assertk assert on the property with the given name.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Assert<T>.prop(name: String): Assert<*> {
    return prop(name) { assertedValue ->
        assertedValue::class.memberProperties
            .first { it.name == name }.let { property ->
                property.isAccessible = true
                (property as KProperty1<T, *>).get(assertedValue)
            }
    }
}