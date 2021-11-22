package io.qalipsis.core.configuration

/**
 * Set of environments available to configure QALIPSIS at startup.
 *
 * @author Eric Jessé
 */
object ExecutionEnvironments {
    /**
     * Enables for in-memory repositories instead of persistent ones.
     */
    const val VOLATILE = "volatile"

    /**
     * Automatically starts a campaign when the instance is ready. Is only applicable in
     * conjunction with [STANDALONE].
     */
    const val AUTOSTART = "autostart"

    /**
     * Starts an instance with head and factory running aside in the same JVM.
     */
    const val STANDALONE = "standalone"

    /**
     * Enables the integration with Redis to support data caching and messaging.
     */
    const val REDIS = "redis"
}
