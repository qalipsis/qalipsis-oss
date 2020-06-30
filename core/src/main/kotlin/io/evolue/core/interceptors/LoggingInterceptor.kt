package io.evolue.core.interceptors

import io.evolue.core.annotations.LogInput
import io.evolue.core.annotations.LogInputAndOutput
import io.evolue.core.annotations.LogOutput
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import org.slf4j.LoggerFactory
import javax.inject.Singleton

/**
 * Interceptor for the logging annotations.
 *
 * @author Eric Jessé
 */
@Singleton
internal class LoggingInterceptor : MethodInterceptor<Any, Any?> {

    override fun intercept(context: MethodInvocationContext<Any, Any?>): Any? {
        val logInput =
            context.hasAnnotation(LogInput::class.java) || context.hasAnnotation(LogInputAndOutput::class.java)
        val logOutput =
            context.hasAnnotation(LogOutput::class.java) || context.hasAnnotation(LogInputAndOutput::class.java)

        if (!logInput && !logOutput) {
            return context.proceed()
        }

        val logger = LoggerFactory.getLogger(context.declaringType)
        if (logInput) {
            logger.trace(context.parameters.entries
                .map { e -> "${e.key}=${e.value.value}" }
                .joinToString(
                    separator = ", ",
                    prefix = "Input parameters of method ${context.executableMethod.name}: "
                ))
        }
        val result = try {
            context.proceed()
        } catch (t: Throwable) {
            if (logOutput) {
                logger.trace("Exception was thrown by method ${context.executableMethod.name}: ", t)
            }
            throw t
        }
        if (logOutput) {
            logger.trace("Result returned from method ${context.executableMethod.name}: $result")
        }
        return result
    }
}
