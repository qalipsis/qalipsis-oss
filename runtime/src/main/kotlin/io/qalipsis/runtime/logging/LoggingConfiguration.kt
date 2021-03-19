package io.qalipsis.runtime.logging

import io.micronaut.context.annotation.ConfigurationProperties
import java.util.Properties

@ConfigurationProperties("logging")
class LoggingConfiguration {

    var root: String? = null

    var file: NormalLoggingFile? = null

    var events: EventsLoggingFile? = null

    var pattern: String? = null

    var console: Boolean = false

    var level: Properties = Properties()

    @ConfigurationProperties("file")
    class NormalLoggingFile : LoggingFile()

    /**
     * Configuration for the slf4j event publisher.
     */
    @ConfigurationProperties("events")
    class EventsLoggingFile : LoggingFile()

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
         * If false (the default) the appender will statement on appending to a full queue rather than losing the message. Set to true and the appender will just drop the message and will not statement your application.
         */
        var neverBlock = false
    }

}
