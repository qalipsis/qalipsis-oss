/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.steps

import assertk.assertThat
import assertk.assertions.index
import assertk.assertions.isEqualTo
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meter.DummyStepSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
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
            max.isMoreThan(Duration.of(2, ChronoUnit.SECONDS))
//            max.isLessThan(Duration.of(2, ChronoUnit.SECONDS))
//            average.isLessThan(Duration.of(1, ChronoUnit.SECONDS))
            mean.isMoreThan(Duration.of(1, ChronoUnit.SECONDS))
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
