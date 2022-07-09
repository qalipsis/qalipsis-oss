package io.qalipsis.api.processors.injector

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