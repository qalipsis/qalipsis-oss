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

package io.qalipsis.api.scenario

import io.micronaut.context.Qualifier
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.convert.ConversionService
import java.util.Optional

/**
 *  An interface that provides facilities to inject beans and properties.
 *
 *  @author Svetlana Paliashchuk
 */
interface Injector {

    /**
     * Provides an access to the Micronaut ConversionService
     */
    val conversionService: ConversionService<*>

    fun <T> getProperty(@NonNull name: String, @NonNull requiredType: Class<T>): Optional<T>

    fun <T> getProperty(@NonNull name: String, @NonNull requiredType: Class<T>, @Nullable defaultValue: T): T

    fun <T> getRequiredProperty(@NonNull name: String, @NonNull requiredType: Class<T>): T

    fun <T> getBean(@NonNull beanType: Class<T>, @Nullable qualifier: Qualifier<T>): T

    fun <T> getBean(@NonNull beanType: Class<T>): T

    fun <T> findBean(@NonNull beanType: Class<T>, @Nullable qualifier: Qualifier<T>): Optional<T>

    fun <T> findBean(@NonNull beanType: Class<T>): Optional<T>

    fun <T> getBeansOfType(@NonNull beanType: Class<T>): Collection<T>

    fun <T> getBeansOfType(@NonNull beanType: Class<T>, @Nullable qualifier: Qualifier<T>): Collection<T>

}