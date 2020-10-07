package io.qalipsis.runtime.test

import io.qalipsis.runtime.Qalipsis

/**
 *
 * @author Eric Jessé
 */
object QalipsisTestRunner {

    /**
     * Starts a local standalone qalipsis process with the test profile enabled.
     */
    fun execute(vararg args: String) = Qalipsis.start(arrayOf("-e", "test").plus(args))

}