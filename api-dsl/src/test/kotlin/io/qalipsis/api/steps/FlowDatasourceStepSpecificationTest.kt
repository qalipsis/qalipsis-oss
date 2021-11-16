package io.qalipsis.api.steps

import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class FlowDatasourceStepSpecificationTest {

    @Test
    internal fun `should add datasource to the scenario`() {
        val scenario = scenario(
            "my-scenario"
        ) as StepSpecificationRegistry

        val specification: suspend () -> Flow<Int> = { (1..10).asFlow() }
        scenario.datasource(specification)

        assertEquals(DatasourceStepSpecification(specification), scenario.rootSteps[0])
    }

}