package io.qalipsis.core.factory.injector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.Qualifier
import io.micronaut.core.convert.ConversionService
import io.qalipsis.api.processors.injector.Injector
import jakarta.inject.Singleton
import java.util.Optional

/**
 * The default implementation of [Injector] uses the Micronaut [io.micronaut.context.ApplicationContext]
 * to perform the operations.
 *
 * @author Svetlana Paliashchuk
 */
@Singleton
internal class DefaultInjector(
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