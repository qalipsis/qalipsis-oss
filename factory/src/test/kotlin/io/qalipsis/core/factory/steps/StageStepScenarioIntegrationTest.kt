package io.qalipsis.core.factory.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filter
import io.qalipsis.api.steps.flatten
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.mapWithContext
import io.qalipsis.api.steps.onEach
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.stage
import io.qalipsis.api.steps.verify
import io.qalipsis.runtime.test.QalipsisTestRunner
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout


internal class StageStepScenarioIntegrationTest {

    @AfterEach
    internal fun tearDown() {
        StageStepScenario.capturedValues.clear()
        StageStepScenario.repetitionMultiplicationFactor.set(2)
    }

    @Test
    @Timeout(30)
    internal fun `should call all the steps of the stage several times`() {
        val exitCode = QalipsisTestRunner.withScenarios("stage-scenario-test")
            .withConfiguration(
                "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "logging.level.io.qalipsis.core.factory.inmemory=TRACE"
            )
            .execute()

        assertThat(exitCode).isEqualTo(0)
        assertThat(StageStepScenario.capturedValues).all {
            hasSize(StageStepScenario.minionsNumber * 10)
        }
    }

    @Test
    @Timeout(30)
    internal fun `should call all the steps of the stage several times even when there is no output from stage`() {
        val exitCode = QalipsisTestRunner.withScenarios("stage-scenario-test-without-output")
            .withConfiguration(
                "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "logging.level.io.qalipsis.core.factory.inmemory=TRACE"
            )
            .execute()

        assertThat(exitCode).isEqualTo(0)
        assertThat(StageStepScenario.capturedValues).all {
            hasSize(StageStepScenario.minionsNumber * 5)
        }
    }

    @Test
    @Timeout(30)
    internal fun `should call all the steps of the stage several times until the failure`() {
        val exitCode = QalipsisTestRunner.withScenarios("stage-scenario-test-with-failure")
            .withConfiguration(
                "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "logging.level.io.qalipsis.core.factory.inmemory=TRACE"
            )
            .execute()

        assertThat(exitCode).isEqualTo(201)
        assertThat(StageStepScenario.capturedValues).all {
            hasSize(StageStepScenario.minionsNumber * 3) // The values are captured 2 in the same Stage: once before and once after the potentially failing step.
        }
    }
}

object StageStepScenario {

    const val minionsNumber = 20

    private val initialCounter = AtomicInteger(1)

    val repetitionMultiplicationFactor = AtomicInteger(2)

    val capturedValues = concurrentList<String>()

    @Scenario
    fun stageStepScenario() {
        scenario("stage-scenario-test") {
            minionsCount = minionsNumber
            profile { regular(100, minionsNumber) }
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

    @Scenario
    fun stageStepScenarioWithoutOutput() {
        scenario("stage-scenario-test-without-output") {
            minionsCount = minionsNumber
            profile { regular(100, minionsNumber) }
        }
            .start()
            .returns(initialCounter.getAndIncrement())
            .stage {
                map { repetitionMultiplicationFactor.getAndIncrement() }
                    .map { it.toString() }
                    .onEach(capturedValues::add)
                    .filter { false }
            }.configure {
                iterate(5)
            }
    }

    @Scenario
    fun stageStepScenarioWithFailure() {
        val visitedMinions = concurrentSet<MinionId>()

        scenario("stage-scenario-test-with-failure") {
            minionsCount = minionsNumber
            profile { regular(100, minionsNumber) }
        }
            .start()
            .returns(initialCounter.getAndIncrement())
            .stage {
                map { repetitionMultiplicationFactor.getAndIncrement() }
                    .mapWithContext<Int, Pair<MinionId, String>> { ctx, input -> ctx.minionId to input.toString() }
                    .onEach { capturedValues.add(it.second) }
                    .verify {
                        // Fails when the minions comes for the second time.
                        assertThat(visitedMinions.add(it.first)).isTrue()
                    }
                    .map { it.second }
                    .onEach(capturedValues::add)
            }.configure {
                iterate(5)
            }
    }
}
