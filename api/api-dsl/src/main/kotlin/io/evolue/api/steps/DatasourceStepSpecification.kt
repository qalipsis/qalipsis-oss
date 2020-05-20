package io.evolue.api.steps

import io.evolue.api.ScenarioSpecification
import kotlinx.coroutines.flow.Flow

/**
 * Specification for a [io.evolue.api.steps.DatasourceStep].
 *
 * @author Eric Jessé
 */
data class DatasourceStepSpecification<OUTPUT>(
    val specification: suspend () -> Flow<OUTPUT>
) : AbstractStepSpecification<Unit, OUTPUT?, DatasourceStepSpecification<OUTPUT>>()

fun <OUTPUT> ScenarioSpecification.datasource(
    specification: (suspend () -> Flow<OUTPUT>)): DatasourceStepSpecification<OUTPUT> {
    val step = DatasourceStepSpecification(specification)
    this.add(step)
    return step
}