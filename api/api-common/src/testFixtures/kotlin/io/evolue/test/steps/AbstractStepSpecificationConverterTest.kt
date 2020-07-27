package io.evolue.test.steps

import io.evolue.api.events.EventsLogger
import io.evolue.api.orchestration.DirectedAcyclicGraph
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.test.mockk.WithMockk
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.jupiter.api.Test

@WithMockk
abstract class AbstractStepSpecificationConverterTest<T : StepSpecificationConverter<*>> {

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var scenarioSpecification: MutableScenarioSpecification

    @RelaxedMockK
    lateinit var directedAcyclicGraph: DirectedAcyclicGraph

    @RelaxedMockK
    lateinit var mockedRetryPolicy: RetryPolicy

    @InjectMockKs
    lateinit var converter: T

    @Test
    abstract fun `should support expected spec`()

    @Test
    abstract fun `should not support unexpected spec`()
}
