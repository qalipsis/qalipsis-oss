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

import io.micronaut.context.annotation.Value
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.scenario.ClasspathScenarioInitializer
import io.qalipsis.api.serialization.SerializersProvider
import jakarta.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [RecordTypeResolver], allowing only:
 * - the types having a serializer registered in the [SerializersProvider],
 * - built-in value types: boxed primitives, [String], [java.math.BigDecimal], [java.math.BigInteger], [java.util.UUID],
 *   [Unit], [Pair], [Triple] and the types of the package `java.time`,
 * - the [Collection]s and [Map]s of the packages `java.util`, `java.util.concurrent` and `kotlin.collections`,
 * - the types of the packages (and their sub-packages) declaring the loaded scenarios, of the QALIPSIS API and plugins,
 *   and of the packages configured with the property [ALLOWED_PACKAGES_PROPERTY],
 * - the arrays of all the above.
 *
 * The allowance is verified on the name before the class is loaded, and classes are never initialized while resolved.
 *
 * @author Eric Jessé
 */
@Singleton
@OptIn(ExperimentalSerializationApi::class)
class DefaultRecordTypeResolver(
    serializersProvider: SerializersProvider,
    @Value("\${$ALLOWED_PACKAGES_PROPERTY:}") configuredPackages: List<String>
) : RecordTypeResolver {

    private val classLoader = this::class.java.classLoader

    private val registeredTypes: Map<String, Class<*>> =
        serializersProvider.serialFormatWrappersByType.keys.flatMap { kClass ->
            val type = kClass.java
            listOfNotNull(type.name to type, type.canonicalName?.let { it to type })
        }.toMap()

    private val staticallyAllowedPackages =
        (DEFAULT_ALLOWED_PACKAGES + configuredPackages).map { it.trim().trimEnd('.') }.filter(String::isNotBlank)

    private val resolvedTypes = ConcurrentHashMap<String, Class<*>>()

    override fun resolve(typeName: String): Class<*> =
        registeredTypes[typeName] ?: resolvedTypes[typeName] ?: loadIfAllowed(typeName).also { resolvedTypes[typeName] = it }

    private fun loadIfAllowed(typeName: String): Class<*> {
        if (!isAllowedName(typeName)) {
            reject(typeName)
        }
        val type = try {
            Class.forName(typeName, false, classLoader)
        } catch (e: ClassNotFoundException) {
            throw SerializationException("The type $typeName could not be found")
        }
        return type.takeIf(::isAllowedClass) ?: reject(typeName)
    }

    private fun isAllowedName(typeName: String): Boolean {
        val packageName = typeName.substringBeforeLast('.', "")
        return when {
            typeName.startsWith(ARRAY_PREFIX) -> isAllowedArrayName(typeName.trimStart(ARRAY_PREFIX))
            typeName in BUILT_IN_TYPES || packageName in BUILT_IN_PACKAGES || packageName in COLLECTION_PACKAGES -> true
            else -> allowedPackages().any { typeName.startsWith("$it.") }
        }
    }

    /**
     * Verifies the element of an array from its JVM descriptor, without the leading '['.
     */
    private fun isAllowedArrayName(elementDescriptor: String) =
        elementDescriptor.length == 1 || (elementDescriptor.startsWith('L') && elementDescriptor.endsWith(';')
                && isAllowedName(elementDescriptor.substring(1, elementDescriptor.length - 1)))

    private fun isAllowedClass(type: Class<*>): Boolean = when {
        type.isArray -> isAllowedClass(type.componentType)
        type.packageName in COLLECTION_PACKAGES && type.name !in BUILT_IN_TYPES ->
            Collection::class.java.isAssignableFrom(type) || Map::class.java.isAssignableFrom(type)

        else -> true
    }

    private fun allowedPackages() = staticallyAllowedPackages + ClasspathScenarioInitializer.scenariosPackages

    private fun reject(typeName: String): Nothing {
        log.warn { "A record of type $typeName was rejected, because the type is not allowed to be deserialized" }
        throw RejectedRecordTypeException(typeName)
    }

    companion object {

        /**
         * Property to configure additional packages whose types can be deserialized from records, as a comma-separated list.
         */
        const val ALLOWED_PACKAGES_PROPERTY = "serialization.records.allowed-packages"

        private const val ARRAY_PREFIX = '['

        private val DEFAULT_ALLOWED_PACKAGES = listOf("io.qalipsis.api", "io.qalipsis.plugins")

        private val BUILT_IN_TYPES = setOf(
            "java.lang.String",
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Character",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.util.UUID",
            "kotlin.Unit",
            "kotlin.Pair",
            "kotlin.Triple"
        )

        private val BUILT_IN_PACKAGES = setOf("java.time")

        private val COLLECTION_PACKAGES = setOf("java.util", "java.util.concurrent", "kotlin.collections")

        private val log = logger()
    }
}
