package io.evolue.core.annotations

import io.evolue.core.interceptors.LoggingInterceptor
import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type
import org.slf4j.event.Level
import java.lang.annotation.Inherited

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Inherited
@Around
@Type(LoggingInterceptor::class)
annotation class LogOutput(
        /**
         * Level to trace normal output messages.
         */
        val level: Level = Level.TRACE,

        /**
         * Level to trace the exceptions.
         */
        val exceptionLevel: Level = Level.TRACE
)
