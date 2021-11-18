package io.qalipsis.core.factories.orchestration

/**
 * <p>
 * The ScenariosKeeper is composed of a registry to keep the full description of all the scenarios supported by
 * the factory as well as an analyzer in charge of decomposing the scenarios when it receives a directive
 * for it from the head.
 * </p>
 *
 * <p>
 * The decomposition of the scenario (requested from the head as a reference to a directive) is then shared to
 * all the factories via messaging and kept in each registry.
 * </p>
 *
 * @author Eric Jessé
 */
internal interface ScenariosInitializer {

    /**
     * Refreshes the scenarios
     */
    fun refresh()

}
