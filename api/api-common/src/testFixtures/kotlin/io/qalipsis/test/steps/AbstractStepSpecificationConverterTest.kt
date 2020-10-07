package io.qalipsis.test.steps

import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.orchestration.DirectedAcyclicGraph
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.MutableScenarioSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.test.mockk.WithMockk
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
