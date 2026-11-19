/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Builds sample instances of the types registered in a [SerializersModule], in order to verify that all of them
 * can be transported, without listing them by hand.
 *
 * @property nullableAsNull when true, the nullable properties are set to null, otherwise to a sample value
 *
 * @author Eric Jessé
 */
@OptIn(ExperimentalSerializationApi::class)
class RegisteredTypesSamples(
    module: SerializersModule,
    private val nullableAsNull: Boolean = false
) {

    private val subclassesByBase: Map<KClass<*>, List<KClass<*>>> = mutableMapOf<KClass<*>, MutableList<KClass<*>>>()
        .also { result ->
            module.dumpTo(object : SerializersModuleCollector {
                override fun <T : Any> contextual(
                    kClass: KClass<T>,
                    provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>
                ) = Unit

                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>
                ) {
                    result.computeIfAbsent(baseClass) { mutableListOf() }.add(actualClass)
                }

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?
                ) = Unit

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?
                ) = Unit
            })
        }

    /**
     * Returns all the concrete subclasses registered for the polymorphic [baseClass].
     */
    fun concreteSubclassesOf(baseClass: KClass<*>): List<KClass<*>> =
        subclassesByBase[baseClass].orEmpty().filterNot { it.isAbstract }.distinct()

    /**
     * Creates an instance of [type], using the default values of the optional constructor parameters
     * and sample values for the others and for the late-initialized properties.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> sample(type: KClass<T>): T = sample(type, emptyList(), 0) as T

    private fun sample(type: KClass<*>, typeArguments: List<KType?>, depth: Int): Any {
        require(depth < MAX_DEPTH) { "The type $type is too deep to create a sample" }
        return builtInSample(type, typeArguments, depth)
            ?: type.objectInstance
            ?: if (type.isAbstract || type.isSealed || type.java.isInterface) {
                sample(concreteImplementationOf(type), emptyList(), depth + 1)
            } else {
                construct(type, depth)
            }
    }

    private fun builtInSample(type: KClass<*>, typeArguments: List<KType?>, depth: Int): Any? = when {
        type == String::class -> "value"
        type == Int::class -> 1
        type == Long::class -> 1L
        type == Short::class -> 1.toShort()
        type == Byte::class -> 1.toByte()
        type == Double::class -> 1.0
        type == Float::class -> 1.0f
        type == Boolean::class -> true
        type == Char::class -> 'c'
        type == ByteArray::class -> byteArrayOf(1)
        type == Instant::class -> Instant.ofEpochSecond(1)
        type == Duration::class -> Duration.ofSeconds(1)
        type.java.isEnum -> type.java.enumConstants.first()
        type.isSubclassOf(Map::class) -> mapOf(element(typeArguments, 0, depth) to element(typeArguments, 1, depth))
        type.isSubclassOf(Set::class) -> setOf(element(typeArguments, 0, depth))
        type.isSubclassOf(Collection::class) || type == Iterable::class -> listOf(element(typeArguments, 0, depth))
        else -> null
    }

    private fun element(typeArguments: List<KType?>, index: Int, depth: Int): Any {
        val argument = typeArguments.getOrNull(index)
        val classifier = argument?.classifier as? KClass<*> ?: String::class
        return sample(classifier, argument?.arguments.orEmpty().map { it.type }, depth + 1)
    }

    private fun concreteImplementationOf(type: KClass<*>): KClass<*> =
        (type.sealedSubclasses + subclassesByBase[type].orEmpty()).firstOrNull { !it.isAbstract && !it.isSealed }
            ?: throw IllegalArgumentException("No concrete implementation is known for $type")

    private fun construct(type: KClass<*>, depth: Int): Any {
        val constructor = requireNotNull(type.primaryConstructor) { "The type $type has no primary constructor" }
        constructor.isAccessible = true
        val arguments = constructor.parameters.filterNot { it.isOptional }.associateWith { parameter ->
            val classifier = parameter.type.classifier as KClass<*>
            val typeArguments = parameter.type.arguments.map { it.type }
            sampleOrNull(classifier, typeArguments, parameter.type.isMarkedNullable, depth)
        }
        return constructor.callBy(arguments).also { initializeLateProperties(it, depth) }
    }

    private fun sampleOrNull(type: KClass<*>, typeArguments: List<KType?>, nullable: Boolean, depth: Int): Any? =
        when {
            nullable && nullableAsNull -> null
            nullable -> runCatching { sample(type, typeArguments, depth + 1) }.getOrNull()
            else -> sample(type, typeArguments, depth + 1)
        }

    private fun initializeLateProperties(instance: Any, depth: Int) {
        instance::class.memberProperties.filterIsInstance<KMutableProperty1<Any, Any?>>()
            .filter { it.isLateinit }
            .forEach { property ->
                property.isAccessible = true
                val classifier = property.returnType.classifier as KClass<*>
                property.set(instance, sample(classifier, property.returnType.arguments.map { it.type }, depth + 1))
            }
    }

    private companion object {
        const val MAX_DEPTH = 10
    }
}
