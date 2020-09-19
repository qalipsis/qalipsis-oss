package io.evolue.api.orchestration.factories

/**
 *
 *
 * @author Eric Jessé
 */
interface Minion {

    fun onComplete(block: suspend (() -> Unit))

    suspend fun waitForStart()
}
