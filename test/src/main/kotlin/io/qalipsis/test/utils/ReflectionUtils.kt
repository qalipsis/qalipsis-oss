package io.qalipsis.test.utils

import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField


fun Any.setProperty(propertyName: String, value: Any?) {
    val property = this::class.memberProperties.find { it.name == propertyName }
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

@Suppress("UNCHECKED_CAST")
fun <T> Any.getProperty(propertyName: String): T {
    val property = this::class.memberProperties.find { it.name == propertyName }
    return if (property is KProperty<*>) {
        property.isAccessible = true
        property.getter.call(this) as T
    } else {
        throw RuntimeException("The property $propertyName could not be found")
    }
}
