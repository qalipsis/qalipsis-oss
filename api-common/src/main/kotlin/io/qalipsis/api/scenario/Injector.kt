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