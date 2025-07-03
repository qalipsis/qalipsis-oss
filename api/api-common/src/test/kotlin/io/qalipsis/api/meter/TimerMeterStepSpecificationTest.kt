package io.qalipsis.api.meter

import assertk.assertThat
import assertk.assertions.index
import assertk.assertions.isEqualTo
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SimpleStepSpecification
import io.qalipsis.api.steps.execute
import io.qalipsis.api.steps.timer
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @TODO
 * @author Francisca Eze
 */
internal class TimerMeterStepSpecificationTest {

    @Test
    internal fun `should add simple step as next`() {
        val previousStep = DummyStepSpecification()
        val percentiles = mapOf(90.0 to Duration.of(1000, ChronoUnit.MILLIS), 95.0 to Duration.of(2, ChronoUnit.SECONDS))
        previousStep.timer(name = "my-test-timer", percentiles = percentiles) { _, _ ->
           return@timer Duration.of(1800000, ChronoUnit.MILLIS)
        }.shouldFailWhen {
            //not key value
            max.isGreaterThan(Duration.of(2, ChronoUnit.SECONDS))
//            max.isLessThan(Duration.of(2, ChronoUnit.SECONDS))
//            average.isLessThan(Duration.of(1, ChronoUnit.SECONDS))
            mean.isGreaterThan(Duration.of(1, ChronoUnit.SECONDS))
            return@shouldFailWhen 0
        }

//        assertEquals(TimerMeterStepSpecification("my-test-timer", percentiles){}, previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add simple step to scenario`() {
        val scenario = TestScenarioFactory.scenario() as StepSpecificationRegistry
        val specification: suspend (context: StepContext<Unit, String>) -> Unit = { _ -> }
        scenario.execute(specification)

        assertThat(scenario.rootSteps).index(0)
            .isEqualTo(SimpleStepSpecification(specification))
    }

}
