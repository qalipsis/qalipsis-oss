/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.utils

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.model.Sort
import io.qalipsis.core.head.jdbc.entity.Entity
import kotlin.reflect.KClass

internal object SortingUtil {

    /**
     * Sort a list of entities according to the property with the given name.
     *
     * @Param property name of the property to sort. Could also include suffixes ':desc' or ':asc' to define the sorting order, which defaults to asc.
     */
    inline fun <reified T : Entity> List<T>.sortBy(property: String): List<T> {
        return sortBy(T::class, property)
    }

    /**
     * Sort a list of entities according to the property with the given name.
     *
     * @param type the Kotlin type of the items of the list, having [property] as a property.
     * @param property name of the property to sort. Could also include suffixes ':desc' or ':asc' to define the sorting order, which defaults to asc.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Entity> List<T>.sortBy(type: KClass<T>, property: String): List<T> {
        val properties = property.trim().split(":")
        val sortProperty = BeanIntrospection.getIntrospection(type.java).beanProperties
            .firstOrNull {
                it.name == properties.first()
            }
        val sortOrder = properties.last().lowercase()
        return if ("desc" == sortOrder) {
            this.sortedBy { sortProperty?.get(it) as Comparable<Any> }.reversed()
        } else {
            this.sortedBy { sortProperty?.get(it) as Comparable<Any> }
        }
    }

    /**
     * Creates a SQL sorting descriptor to match [type] and the specified [property].
     *
     * @param type the entity Kotlin type of the items of the list, having [property] as a property.
     * @param property name of the property to sort. Could also include suffixes ':desc' or ':asc' to define the sorting order, which defaults to asc.
     */
    fun sort(type: KClass<out Entity>, property: String): Sort? {
        val properties = property.trim().split(":")
        val sortProperty = BeanIntrospection.getIntrospection(type.java).beanProperties
            .firstOrNull {
                it.name == properties.first()
            }
        val sortOrder = properties.last().lowercase()
        return if ("desc" == sortOrder) {
            Sort.of(Sort.Order.desc(sortProperty?.name))
        } else {
            Sort.of(Sort.Order.asc(sortProperty?.name))
        }
    }
}