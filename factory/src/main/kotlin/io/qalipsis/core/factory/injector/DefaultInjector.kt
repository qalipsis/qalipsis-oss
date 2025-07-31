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

package io.qalipsis.core.factory.injector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.Qualifier
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ConversionService
import io.qalipsis.api.scenario.Injector
import jakarta.inject.Singleton
import java.util.Optional

/**
 * The default implementation of [Injector] uses the Micronaut [io.micronaut.context.ApplicationContext]
 * to perform the operations.
 *
 * @author Svetlana Paliashchuk
 */
@Singleton
@Requires(missingBeans = [Injector::class])
class DefaultInjector(
    private val applicationContext: ApplicationContext,
    override val conversionService: ConversionService<*>
) : Injector {

    override fun <T> getProperty(name: String, requiredType: Class<T>): Optional<T> {
        return applicationContext.getProperty(name, requiredType)
    }

    override fun <T> getProperty(name: String, requiredType: Class<T>, defaultValue: T): T {
        return applicationContext.getProperty(name, requiredType, defaultValue)!!
    }

    override fun <T> getRequiredProperty(name: String, requiredType: Class<T>): T {
        return applicationContext.getRequiredProperty(name, requiredType)
    }

    override fun <T> getBean(beanType: Class<T>, qualifier: Qualifier<T>): T {
        return applicationContext.getBean(beanType, qualifier)
    }

    override fun <T> getBean(beanType: Class<T>): T {
        return applicationContext.getBean(beanType)
    }

    override fun <T> findBean(beanType: Class<T>, qualifier: Qualifier<T>): Optional<T> {
        return applicationContext.findBean(beanType, qualifier)
    }

    override fun <T> findBean(beanType: Class<T>): Optional<T> {
        return applicationContext.findBean(beanType)
    }

    override fun <T> getBeansOfType(beanType: Class<T>): Collection<T> {
        return applicationContext.getBeansOfType(beanType)
    }

    override fun <T> getBeansOfType(beanType: Class<T>, qualifier: Qualifier<T>): Collection<T> {
        return applicationContext.getBeansOfType(beanType, qualifier)
    }

}