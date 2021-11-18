package io.qalipsis.core.configuration

/**
 *
 * @author Eric Jessé
 */
object ExecutionEnvironments {
    /**
     * Enables for in-memory repositories instead of persistent ones.
     */
    const val ENV_VOLATILE = "volatile"

    /**
     * Automatically starts a campaign when the instance is ready. Is only applicable in
     * conjunction with [ENV_STANDALONE].
     */
    const val ENV_AUTOSTART = "autostart"

    /**
     * Starts an instance with head and factory running aside in the same JVM.
     */
    const val ENV_STANDALONE = "standalone"
}
