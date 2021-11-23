package io.qalipsis.core.factory.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.flatten
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.stage
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger


internal class StageStepScenarioIntegrationTest {

    @Test
    @Timeout(20)
    internal fun `should call all the steps of the group several times`() {
        StageStepScenario.capturedValues.clear()
        val exitCode = QalipsisTestRunner.withScenarios("group-scenario-test").execute()

        assertThat(exitCode).isEqualTo(0)
        assertThat(StageStepScenario.capturedValues).all {
            hasSize(StageStepScenario.minionsNumber * 10)
        }
    }
}

object StageStepScenario {

    const val minionsNumber = 20

    private val initialCounter = AtomicInteger(1)

    private val repetitionMultiplicationFactor = AtomicInteger(2)

    val capturedValues = concurrentList<String>()

    @Scenario
    fun stageStepScenario() {
        scenario("group-scenario-test") {
            minionsCount = minionsNumber
            rampUp { regular(100, minionsNumber) }
        }
            .start()
            .returns(initialCounter.getAndIncrement())
            .stage {
                map { it + repetitionMultiplicationFactor.getAndIncrement() }
                    .map { arrayOf(it, it + 1000) }.flatten().map { it.toString() }
            }.configure {
                iterate(5)
            }
            .onEach(capturedValues::add)
    }
}
