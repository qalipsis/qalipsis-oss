package io.evolue.api.steps

import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.scenario.ScenarioSpecification
import kotlinx.coroutines.flow.Flow

/**
 * Specification for a [io.evolue.api.steps.DatasourceStep].
 *
 * @author Eric Jessé
 */
data class DatasourceStepSpecification<OUTPUT>(
        val specification: suspend () -> Flow<OUTPUT>
) : AbstractStepSpecification<Unit, OUTPUT?, DatasourceStepSpecification<OUTPUT>>()

/**
 * Simple datasource used to generate data for later steps. This step is not a singleton and executes once per minion.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.datasource(
        specification: (suspend () -> Flow<OUTPUT>)): DatasourceStepSpecification<OUTPUT> {
    val step = DatasourceStepSpecification(specification)
    (this as MutableScenarioSpecification).add(step)
    return step
}
