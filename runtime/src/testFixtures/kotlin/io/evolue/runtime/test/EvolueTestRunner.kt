package io.evolue.runtime.test

import io.evolue.runtime.Evolue

/**
 *
 * @author Eric Jessé
 */
object EvolueTestRunner {

    /**
     * Starts a local standalone evolue process with the test profile enabled.
     */
    fun execute(vararg args: String) = Evolue.start(arrayOf("-e", "test").plus(args))

}