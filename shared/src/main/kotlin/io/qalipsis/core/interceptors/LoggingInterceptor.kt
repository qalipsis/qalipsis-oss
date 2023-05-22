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

package io.qalipsis.core.interceptors

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.ExecutableMethod
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.annotations.LogOutput
import mu.KLogger
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation

/**
 * Interceptor for the logging annotations.
 *
 * @author Eric Jessé
 */
@InterceptorBean(LogInput::class, LogOutput::class, LogInputAndOutput::class)
@Requires(property = "logging.annotations.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
internal class LoggingInterceptor : MethodInterceptor<Any, Any> {

    private val loggers = ConcurrentHashMap<Class<*>, KLogger>()

    private val annotationsByContext = ConcurrentHashMap<ExecutableMethod<*, *>, LoggingContext?>()

    private val loggingCalls = mapOf<Level, ((KLogger, () -> String) -> Unit)>(
        Level.TRACE to { logger, message -> if (logger.isTraceEnabled) logger.trace { message() } },
        Level.DEBUG to { logger, message -> if (logger.isDebugEnabled) logger.debug { message() } },
        Level.INFO to { logger, message -> if (logger.isInfoEnabled) logger.info { message() } },
        Level.WARN to { logger, message -> if (logger.isWarnEnabled) logger.warn { message() } },
        Level.ERROR to { logger, message -> if (logger.isErrorEnabled) logger.error { message() } },
    )

    private val exceptionLoggingCalls = mapOf<Level, ((KLogger, Throwable, () -> String) -> Unit)>(
        Level.TRACE to { logger, t, message -> if (logger.isTraceEnabled) logger.trace(t) { message() } },
        Level.DEBUG to { logger, t, message -> if (logger.isDebugEnabled) logger.debug(t) { message() } },
        Level.INFO to { logger, t, message -> if (logger.isInfoEnabled) logger.info(t) { message() } },
        Level.WARN to { logger, t, message -> if (logger.isWarnEnabled) logger.warn(t) { message() } },
        Level.ERROR to { logger, t, message -> if (logger.isErrorEnabled) logger.error(t) { message() } },
    )

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val contextLevels =
            annotationsByContext.computeIfAbsent(context.executableMethod) {
                buildLoggingContext(context)
            }

        contextLevels?.input?.let { logger ->
            logger.invoke {
                context.parameters.entries.asSequence()
                    .filterNot { it.value.value is Continuation<*> }.joinToString(
                        separator = ", ",
                        prefix = "Method ${context.executableMethod.name} - INPUT: "
                    ) { e -> "${e.key}=${e.value.value}" }
            }
        }

        contextLevels?.callstack?.let { logger ->
            logger.invoke {
                try {
                    throw RuntimeException()
                    ""
                } catch (e: Exception) {
                    e.stackTrace.asSequence()
                        .dropWhile {
                            it.className != "${context.executableMethod.declaringType}"
                                    && it.methodName != context.executableMethod.name
                        }
                        .drop(2) // Drop the call of the Micronaut enhanced, and the one of the context argument.
                        .joinToString(
                            separator = "\n\t\t",
                            prefix = "Method ${context.executableMethod.name} - CALL STACK: \n\t\t"
                        ) { s -> "$s" }
                }
            }
        }

        val result = try {
            context.proceed()
        } catch (t: Throwable) {
            contextLevels?.exception?.let { logger ->
                logger.invoke(t) {
                    "Method ${context.executableMethod.name} - EXCEPTION: $t"
                }
            }
            throw t
        }

        contextLevels?.output?.let { logger ->
            logger.invoke {
                "Method ${context.executableMethod.name} - OUTPUT: ${if (result == Unit) "<The function has no return type>" else result}"
            }
        }

        return result
    }

    private fun buildLoggingContext(
        context: MethodInvocationContext<Any, Any>
    ): LoggingContext? {
        val logInput =
            context.getAnnotation(LogInput::class.java) ?: context.getAnnotation(LogInputAndOutput::class.java)
        val logOutput =
            context.getAnnotation(LogOutput::class.java) ?: context.getAnnotation(LogInputAndOutput::class.java)
        val inputLevel = logInput?.enumValue("level", Level::class.java)?.orElse(Level.TRACE)
        val logCall = logInput?.booleanValue("callstack")?.orElse(false) ?: false
        val logCallLevel =
            if (logCall) logInput?.enumValue("callstackLevel", Level::class.java)?.orElse(Level.TRACE) else null
        val outputLevel = logOutput?.enumValue("level", Level::class.java)?.orElse(Level.TRACE)
        val exceptionLevel = logOutput?.enumValue("exceptionLevel", Level::class.java)?.orElse(Level.TRACE)?.let {
            if (it.toInt() < outputLevel!!.toInt()) {
                outputLevel
            } else {
                it
            }
        }

        return if (logInput != null || logOutput != null) {
            val logger = loggers.computeIfAbsent(context.declaringType) { type ->
                KotlinLogging.logger(LoggerFactory.getLogger(type))
            }
            LoggingContext(
                input = inputLevel?.let { level -> { message -> loggingCalls[level]?.invoke(logger, message) } },
                callstack = logCallLevel?.let { level -> { message -> loggingCalls[level]?.invoke(logger, message) } },
                output = outputLevel?.let { level -> { message -> loggingCalls[level]?.invoke(logger, message) } },
                exception = exceptionLevel?.let { level ->
                    { t, message ->
                        exceptionLoggingCalls[level]?.invoke(logger, t, message)
                    }
                }
            )
        } else {
            null
        }
    }

    private class LoggingContext(
        val input: ((() -> String) -> Unit)?,
        val callstack: ((() -> String) -> Unit)?,
        val output: ((() -> String) -> Unit)?,
        val exception: ((Throwable, () -> String) -> Unit)?,
    )


    private companion object {
        val loggerClass: String = LoggingInterceptor::class.qualifiedName!!
    }

}
