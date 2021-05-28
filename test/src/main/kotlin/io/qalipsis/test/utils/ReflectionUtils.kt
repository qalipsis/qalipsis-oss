package io.qalipsis.test.utils

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

/**
 * Sets [value] in the property or field called [propertyName] of the instance.
 */
fun Any.setProperty(propertyName: String, value: Any?) {
    val property = findProperty<Any>(this::class, propertyName)
    if (property is KMutableProperty<*>) {
        property.isAccessible = true
        property.setter.call(this, value)
    } else if (property is KProperty<*>) {
        property.javaField!!.also {
            it.isAccessible = true
            it.set(this, value)
        }
    } else {
        throw RuntimeException("The property $propertyName could not be found")
    }
}

/**
 * Returns the value of the property or field called [propertyName] of the instance.
 */
@Suppress("UNCHECKED_CAST")
fun <T> Any.getProperty(propertyName: String): T {
    val property = findProperty<T>(this::class, propertyName)
    return if (property is KProperty<*>) {
        property.isAccessible = true
        property.getter.call(this) as T
    } else {
        throw RuntimeException("The property $propertyName could not be found")
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> findProperty(instanceClass: KClass<*>, propertyName: String): KProperty1<T, *>? {
    return (instanceClass.takeIf { it.memberProperties.firstOrNull { it.name == propertyName } != null }
        ?: instanceClass.superclasses.firstOrNull { it.memberProperties.find { it.name == propertyName } != null })
        ?.memberProperties?.firstOrNull { it.name == propertyName } as KProperty1<T, *>?
}
