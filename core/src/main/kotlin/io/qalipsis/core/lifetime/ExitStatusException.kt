package io.qalipsis.core.lifetime

/**
 * Exception able to force the exit value of the QALIPSIS process.
 *
 * @author Eric Jessé
 */
class ExitStatusException(override val cause: Throwable, val exitStatus: Int) : Exception(cause)