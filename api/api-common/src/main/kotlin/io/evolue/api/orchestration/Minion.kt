package io.evolue.api.orchestration

/**
 *
 *
 * @author Eric Jessé
 */
interface Minion {

    fun onComplete(block: suspend (() -> Unit))

    suspend fun waitForStart()
}