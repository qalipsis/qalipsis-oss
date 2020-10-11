package io.qalipsis.core.annotations

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type
import io.qalipsis.core.interceptors.LoggingInterceptor
import org.slf4j.event.Level
import java.lang.annotation.Inherited

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@Around
@Type(LoggingInterceptor::class)
annotation class LogInput(
        /**
         * Level to trace normal input messages.
         */
        val level: Level = Level.TRACE
)
