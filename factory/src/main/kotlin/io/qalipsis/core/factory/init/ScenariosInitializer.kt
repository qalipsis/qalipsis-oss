package io.qalipsis.core.factory.init

/**
 * Interface of a service in charge of reading the scenario specifications and converting them into executable
 * scenarios.
 *
 * @author Eric Jessé
 */
internal interface ScenariosInitializer {

    /**
     * Refreshes the scenarios
     */
    fun refresh()

}
