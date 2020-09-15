package io.evolue.core.interceptors

import io.evolue.core.annotations.LogInput
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.annotations.LogOutput
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.inject.ExecutableMethod
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

/**
 * Interceptor for the logging annotations.
 *
 * @author Eric Jessé
 */
@Singleton
internal class LoggingInterceptor : MethodInterceptor<Any, Any?> {

    private val loggers = ConcurrentHashMap<Class<*>, Logger>()

    private val annotationsByContext = ConcurrentHashMap<ExecutableMethod<*, *>, LoggingContext?>()

    private val loggingCalls = mapOf<Level, ((Logger, () -> String) -> Unit)>(
            Level.TRACE to { logger, message -> if (logger.isTraceEnabled()) logger.trace(message.invoke()) },
            Level.DEBUG to { logger, message -> if (logger.isDebugEnabled()) logger.debug(message.invoke()) },
            Level.INFO to { logger, message -> if (logger.isInfoEnabled()) logger.info(message.invoke()) },
            Level.WARN to { logger, message -> if (logger.isWarnEnabled()) logger.warn(message.invoke()) },
            Level.ERROR to { logger, message -> if (logger.isErrorEnabled()) logger.error(message.invoke()) },
    )

    private val exceptionLoggingCalls = mapOf<Level, ((Logger, Throwable, () -> String) -> Unit)>(
            Level.TRACE to { logger, t, message -> if (logger.isTraceEnabled()) logger.trace(message.invoke(), t) },
            Level.DEBUG to { logger, t, message -> if (logger.isDebugEnabled()) logger.trace(message.invoke(), t) },
            Level.INFO to { logger, t, message -> if (logger.isInfoEnabled()) logger.trace(message.invoke(), t) },
            Level.WARN to { logger, t, message -> if (logger.isWarnEnabled()) logger.trace(message.invoke(), t) },
            Level.ERROR to { logger, t, message -> if (logger.isErrorEnabled()) logger.trace(message.invoke(), t) },
    )

    override fun intercept(context: MethodInvocationContext<Any, Any?>): Any? {
        val contextLevels =
            annotationsByContext.computeIfAbsent(context.executableMethod) {
                buildLoggingContext(context)
            }

        contextLevels?.input?.let { logger ->
            logger.invoke {
                context.parameters.entries
                    .map { e -> "${e.key}=${e.value.value}" }
                    .joinToString(
                            separator = ", ",
                            prefix = "Method ${context.executableMethod.name} - INPUT: "
                    )
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
                "Method ${context.executableMethod.name} - OUTPUT: $result"
            }
        }

        return result
    }

    private fun buildLoggingContext(
            context: MethodInvocationContext<Any, Any?>): LoggingContext? {
        val logInput =
            context.getAnnotation(LogInput::class.java) ?: context.getAnnotation(LogInputAndOutput::class.java)
        val logOutput =
            context.getAnnotation(LogOutput::class.java) ?: context.getAnnotation(LogInputAndOutput::class.java)
        val inputLevel = logInput?.enumValue("level", Level::class.java)?.orElse(null)
        val outputLevel = logOutput?.enumValue("level", Level::class.java)?.orElse(null)
        val exceptionLevel = logOutput?.enumValue("exceptionLevel", Level::class.java)?.orElse(null)?.let {
            if (it.toInt() < outputLevel!!.toInt()) {
                outputLevel
            } else {
                it
            }
        }

        return if (logInput != null || logOutput != null) {
            val logger = loggers.computeIfAbsent(context.declaringType) { type -> LoggerFactory.getLogger(type) }
            LoggingContext(
                    inputLevel?.let { level -> { message -> loggingCalls[level]?.invoke(logger, message) } },
                    outputLevel?.let { level -> { message -> loggingCalls[level]?.invoke(logger, message) } },
                    exceptionLevel?.let { level ->
                        { t, message ->
                            exceptionLoggingCalls[exceptionLevel]?.invoke(logger, t, message)
                        }
                    },

                    )
        } else {
            null
        }
    }

    private class LoggingContext(
            val input: ((() -> String) -> Unit)?,
            val output: ((() -> String) -> Unit)?,
            val exception: ((Throwable, () -> String) -> Unit)?,
    )

}
