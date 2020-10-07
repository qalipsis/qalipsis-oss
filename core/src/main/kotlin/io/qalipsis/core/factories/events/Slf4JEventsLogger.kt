package io.qalipsis.core.factories.events

import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsLogger
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory
import javax.inject.Singleton

/**
 *
 * Default event logger, when no other is set.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(missingBeans = [EventsLogger::class])
class Slf4JEventsLogger : EventsLogger {

    override fun log(level: EventLevel, name: String, value: Any?, tagsSupplier: () -> Map<String, String>) {
        when {
            // Since the tagsSupplier could have a cost to execute, we first check that the execution is absolutely
            // required by verify the enabled level on the logger.
            level == EventLevel.ERROR && logger.isErrorEnabled -> logError(name, value, tagsSupplier())
            level == EventLevel.WARN && logger.isWarnEnabled -> logWarn(name, value, tagsSupplier())
            level == EventLevel.INFO && logger.isInfoEnabled -> logInfo(name, value, tagsSupplier())
            level == EventLevel.DEBUG && logger.isDebugEnabled -> logDebug(name, value, tagsSupplier())
            level == EventLevel.TRACE && logger.isTraceEnabled -> logTrace(name, value, tagsSupplier())
        }
    }

    override fun log(level: EventLevel, name: String, value: Any?, tags: Map<String, String>) {
        when (level) {
            EventLevel.ERROR -> logError(name, value, tags)
            EventLevel.WARN -> logWarn(name, value, tags)
            EventLevel.INFO -> logInfo(name, value, tags)
            EventLevel.DEBUG -> logDebug(name, value, tags)
            EventLevel.TRACE -> logTrace(name, value, tags)
            else -> { /* Nothing to do */
            }
        }
    }

    private fun logError(name: String, value: Any?, tags: Map<String, String>) {
        logger.error("${name};${value ?: ""};${tags.map { "${it.key}=${it.value}" }.joinToString(",")}")
    }

    private fun logWarn(name: String, value: Any?, tags: Map<String, String>) {
        logger.warn("${name};${value ?: ""};${tags.map { "${it.key}=${it.value}" }.joinToString(",")}")
    }

    private fun logInfo(name: String, value: Any?, tags: Map<String, String>) {
        logger.info("${name};${value ?: ""};${tags.map { "${it.key}=${it.value}" }.joinToString(",")}")
    }

    private fun logDebug(name: String, value: Any?, tags: Map<String, String>) {
        logger.debug("${name};${value ?: ""};${tags.map { "${it.key}=${it.value}" }.joinToString(",")}")
    }

    private fun logTrace(name: String, value: Any?, tags: Map<String, String>) {
        logger.trace("${name};${value ?: ""};${tags.map { "${it.key}=${it.value}" }.joinToString(",")}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger("events")
    }
}
