package io.qalipsis.core.configuration

/**
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
}
