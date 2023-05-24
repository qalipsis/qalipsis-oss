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

import ch.qos.logback.classic.Level
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.env.Environment
import io.micronaut.core.naming.conventions.StringConvention
import javax.annotation.PostConstruct

@ConfigurationProperties("logging")
internal class LoggingConfiguration(
    val environment: Environment
) {

    var root: String? = null

    var file: NormalLoggingFile? = null

    var events: EventsLoggingFile? = null

    var pattern: String? = null

    var console: Boolean = false

    lateinit var loggingLevels: Map<String, Level>

    @ConfigurationProperties("file")
    class NormalLoggingFile : LoggingFile()

    /**
     * Configuration for the slf4j event publisher.
     */
    @ConfigurationProperties("events")
    class EventsLoggingFile : LoggingFile()

    @PostConstruct
    fun init() {
        val properties: MutableMap<String, Any> = HashMap(environment.getProperties("logging.level"))
        // Using raw keys here allows configuring log levels for camelCase package names in application.yml
        properties.putAll(environment.getProperties("logging.level", StringConvention.RAW))
        val levels = mutableMapOf<String, Level>()
        properties.forEach { (loggerPrefix, levelValue) ->
            levels[loggerPrefix] = Level.valueOf("$levelValue".uppercase())
        }
        loggingLevels = levels
    }

    open class LoggingFile {

        /**
         * Full path of the logging file.
         */
        var path: String? = null

        /**
         * Max size of each rolling file.
         */
        var maxSize: String? = null

        /**
         * Maximal number of kept rolling file.
         */
        var maxHistory: Int = 0

        /**
         * Total allowed capacity of the logs.
         */
        var totalCapacity: String? = null

        var pattern: String? = null

        /**
         * Enables an async appender for better performance.
         */
        var async: Boolean = false

        /**
         * Size of the messages queue when async is enabled.
         */
        var queueSize = 1024

        /**
         * Do not include caller data for event better performances of the async appender.
         */
        var includeCallerData = false

        /**
         * If false (the default) the appender will block on appending to a full queue rather than losing the message. Set to true and the appender will just drop the message and will not block your application.
         */
        var neverBlock = false
    }
}
