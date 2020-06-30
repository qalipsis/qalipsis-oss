package io.evolue.runtime.logging

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import io.evolue.runtime.Configurer
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Singleton

/**
 * Configures the loggers dynamically.
 *
 * @author Eric Jessé
 */
@Singleton
open class LogbackConfigurer(
        private val config: LoggingConfiguration
) : Configurer {

    override fun configure() {
        val loggerContext = (LoggerFactory.getILoggerFactory() as LoggerContext)
        val defaultPattern =
            if (config.pattern.isNullOrBlank()) "%d{yyyy-MM-dd'T'HH:mm:ss.SSS,UTC}Z %5p --- [%t] %logger.%M.%L : %m%n}" else config.pattern
        loggerContext.putProperty("LOG_PATTERN", defaultPattern)

        val rootLogger = loggerContext.getLogger("ROOT")
        // If the logs are expected in the console.
        if (!config.console) {
            log.info("Disabling the console logs")
            rootLogger.detachAppender("console")
        }

        // If the logs are expected in the file.
        config.file?.let { configFile ->
            if (!configFile.path.isNullOrBlank()) {
                rootLogger.apply {
                    addAppender(buildRollingFileAppender("fileAppender", loggerContext, configFile, defaultPattern))
                }
            }
        }

        // Configuration of the events logging.
        config.events?.let { configFile ->
            if (!configFile.path.isNullOrBlank()) {
                loggerContext.getLogger("events").apply {
                    addAppender(buildRollingFileAppender("eventsAppender", loggerContext, configFile, defaultPattern))
                    isAdditive = false
                }
            }
        }

        config.root?.let { level ->
            loggerContext.getLogger("ROOT").level = Level.valueOf(level)
        }

        config.level.mapKeys { it.key as String }.mapValues { Level.valueOf(it.value as String) }
            .forEach { (logger, level) ->
                loggerContext.getLogger(logger).level = level
            }
    }

    private fun buildRollingFileAppender(name: String, context: LoggerContext,
            configFile: LoggingConfiguration.LoggingFile,
            defaultPattern: String?): Appender<ILoggingEvent> {
        val configuredMaxSize = if (configFile.maxSize.isNullOrBlank()) "30MB" else configFile.maxSize
        val configuredTotalCap = if (configFile.totalCapacity.isNullOrBlank()) "100MB" else configFile.totalCapacity
        val configuredMaxHistory = configFile.maxHistory

        val appender = RollingFileAppender<ILoggingEvent>().also { app ->
            app.name = name
            app.context = context
            app.file = File(configFile.path).absolutePath
            app.rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().also { policy ->
                policy.context = context
                policy.setParent(app)
                policy.fileNamePattern = "${configFile.path}.%d{yyyy-MM-dd}.%i.gz"
                policy.setMaxFileSize(FileSize.valueOf(configuredMaxSize))
                policy.maxHistory = configuredMaxHistory
                policy.setTotalSizeCap(FileSize.valueOf(configuredTotalCap))
                policy.start()
            }
            app.encoder = PatternLayoutEncoder().also { enc ->
                enc.context = context
                enc.setParent(app)
                enc.pattern = if (!configFile.pattern.isNullOrBlank()) configFile.pattern else defaultPattern
                enc.charset = StandardCharsets.UTF_8
                enc.start()
            }
            app.start()
        }
        return if (configFile.async) AsyncAppender().also {
            it.context = context
            it.addAppender(appender)
            it.queueSize = configFile.queueSize
            it.isNeverBlock = configFile.neverBlock
            it.isIncludeCallerData = configFile.includeCallerData
            it.discardingThreshold = 0
            it.start()
        } else appender
    }

    companion object {

        @Suppress("JAVA_CLASS_ON_COMPANION")
        @JvmStatic
        private val log = LoggerFactory.getLogger(javaClass.enclosingClass)
    }
}
