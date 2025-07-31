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

package io.qalipsis.runtime.logging

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import io.micronaut.context.annotation.Property
import io.qalipsis.api.config.EventsConfig
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.runtime.Configurer
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Configures the loggers dynamically.
 *
 * @author Eric Jessé
 */
@Singleton
class LogbackConfigurer(
    private val config: LoggingConfiguration,
    @Property(name = EventsConfig.EXPORT_ENABLED, defaultValue = "false") private val enabledEventsPublisher: Boolean,
    @Property(
        name = "${EventsConfig.EXPORT_CONFIGURATION}.slf4j.enabled",
        defaultValue = "false"
    ) private val enabledSlf4JEventsPublisher: Boolean,
) : Configurer {

    override fun configure() {
        if (!config.skip) {
            val loggerContext = (LoggerFactory.getILoggerFactory() as LoggerContext)
            val defaultPattern = config.console.pattern

            val rootLogger = loggerContext.getLogger("ROOT")
            // If the logs are expected in the console.
            if (!config.console.enabled) {
                log.info { "Disabling the console logs" }
                rootLogger.detachAppender("console")
            } else if (rootLogger.getAppender("console") == null) {
                val consoleAppender = ConsoleAppender<ILoggingEvent>()
                consoleAppender.name = "console"
                consoleAppender.encoder = PatternLayoutEncoder().also {
                    it.pattern = config.console.pattern
                    it.charset = StandardCharsets.UTF_8
                }
                consoleAppender.addFilter(ThresholdFilter().also {
                    it.setLevel(config.console.minLogLevel)
                })
                consoleAppender.start()
                rootLogger.addAppender(consoleAppender)
            }
            configureEventsLogging(loggerContext, defaultPattern)

            // If the logs are expected in the file.
            config.file?.let { configFile ->
                if (!configFile.path.isNullOrBlank()) {
                    rootLogger.apply {
                        addAppender(buildRollingFileAppender("fileAppender", loggerContext, configFile, defaultPattern))
                    }
                }
            }

            config.root?.let { level ->
                loggerContext.getLogger("ROOT").level = Level.valueOf(level)
            }

            config.loggingLevels.forEach { (logger, level) ->
                loggerContext.getLogger(logger).level = level
            }
        }
    }

    private fun configureEventsLogging(loggerContext: LoggerContext, defaultPattern: String) {
        if (enabledEventsPublisher && enabledSlf4JEventsPublisher && !config.events.path.isNullOrBlank()) {
            loggerContext.getLogger("events").apply {
                addAppender(
                    /* newAppender = */ buildRollingFileAppender(
                        "eventsAppender",
                        loggerContext,
                        config.events,
                        defaultPattern,
                    )
                )
                isAdditive = false
            }
        }
    }

    private fun buildRollingFileAppender(
        name: String, context: LoggerContext,
        configFile: LoggingConfiguration.LoggingFile,
        defaultPattern: String?
    ): Appender<ILoggingEvent> {
        val configuredMaxSize = if (configFile.maxSize.isNullOrBlank()) "30MB" else configFile.maxSize
        val configuredTotalCap = if (configFile.totalCapacity.isNullOrBlank()) "100MB" else configFile.totalCapacity
        val configuredMaxHistory = configFile.maxHistory

        val appender = RollingFileAppender<ILoggingEvent>().also { app ->
            app.name = name
            app.context = context
            app.file = File(configFile.path!!).absolutePath
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

        @JvmStatic
        private val log = logger()
    }
}
