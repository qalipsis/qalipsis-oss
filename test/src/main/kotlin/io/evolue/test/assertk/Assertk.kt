package io.evolue.test.assertk

import assertk.Assert
import assertk.assertions.isNotNull
import assertk.assertions.prop
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
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
        assertedValue::class.memberProperties
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
        assertedValue::class.memberProperties
            .first { it.name == name }.let { property ->
                property.isAccessible = true
                (property as KProperty1<Any, *>).get(assertedValue)
            }
    }
}
