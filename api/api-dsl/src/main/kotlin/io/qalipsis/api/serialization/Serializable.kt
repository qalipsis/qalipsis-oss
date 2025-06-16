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

package io.qalipsis.api.serialization

import io.qalipsis.api.serialization.Serializable.Format.AUTO
import javax.validation.constraints.NotEmpty
import kotlin.reflect.KClass

/**
 * Annotation to set on classes or files to trigger the creation of a QALIPSIS serialization wrapper, for types
 * supporting the native kotlin serialization, but compiled in third-parties libraries.
 *
 * Classes annotated with the [kotlinx.serialization.Serializable] and compiled with the QALIPSIS processors
 * library in the Kapt classpath do not need to additionally support [Serializable].
 *
 * See [the official documentation](https://kotlinlang.org/docs/serialization.html) for more details.
 *
 * @property types types for which a QALIPSIS serialization wrapper should be created, they should not have generic types
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
annotation class Serializable(

    @get:NotEmpty
    val types: Array<KClass<*>>,

    /**
     * Default format for the class
     */
    val format: Format = AUTO

) {
    enum class Format {
        /**
         * Uses the best (from performance perspective) serializer present in the class path.
         */
        AUTO,

        /**
         * Uses the JSON serializer.
         */
        JSON,

        /**
         * Uses the protobuf serializer.
         */
        PROTOBUF
    }
}
