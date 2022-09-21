/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.steps.join

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.filter
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.singletonPipe
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

internal class InnerJoinStepScenarioIntegrationTest {

    @AfterEach
    internal fun tearDown() {
        InnerJoinStepScenario.capturedValues.clear()
    }

    @Test
    @Timeout(20)
    internal fun `should call execute the inner join scenario with singleton`() {
        val exitCode = QalipsisTestRunner.withScenarios("inner-join-scenario-test")
            .withConfiguration(
                "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "logging.level.io.qalipsis.api.messaging=TRACE",
                "logging.level.io.qalipsis.api.messaging.subscriptions=TRACE"
            )
            .execute()
        assertThat(exitCode).isEqualTo(0)
        assertThat(InnerJoinStepScenario.capturedValues).all {
            hasSize(InnerJoinStepScenario.minionsNumber)
        }
    }

    @Test
    @Timeout(20)
    internal fun `should call execute the inner join scenario with singleton when all records are discarded`() {
        val exitCode = QalipsisTestRunner.withScenarios("inner-join-scenario-test-without-output")
            .withConfiguration(
                "logging.level.io.qalipsis.core.factory.orchestration=DEBUG",
                "logging.level.io.qalipsis.core.factory.orchestration.directives.listeners=TRACE",
                "logging.level.io.qalipsis.core.head.campaign=TRACE",
                "logging.level.io.qalipsis.api.messaging=TRACE",
                "logging.level.io.qalipsis.api.messaging.subscriptions=TRACE"
            )
            .execute()

        assertThat(exitCode).isEqualTo(0)
        assertThat(InnerJoinStepScenario.capturedValues).isEmpty()
    }
}

object InnerJoinStepScenario {

    const val minionsNumber = 2

    val capturedValues = concurrentList<Int>()

    @Scenario("inner-join-scenario-test")
    fun innerJoinScenario() {

        val counter1 = AtomicInteger(0)
        val counter2 = AtomicInteger(0)

        scenario {
            minionsCount = minionsNumber
            profile { regular(100, minionsNumber) }
        }
            .start()
            .returns<Int> { counter1.getAndIncrement() }.configure {
                name = "left-return"
            }
            .innerJoin(
                using = {
                    it.value
                },
                on = {
                    // The root step is not a singleton, hence should be repeated as much as required.
                    it.returns<Int> { counter2.getAndIncrement() }
                        .configure {
                            iterate(minionsNumber.toLong())
                            name = "right-return"
                        }
                        // A singleton step is voluntary added in the between.
                        .singletonPipe().configure {
                            name = "singleton-pipe"
                        }
                },
                having = {
                    it.value
                }
            ).configure {
                name = "inner-join"
            }
            .filterNotNull()
            .map { it.first }
            .onEach(capturedValues::add)
    }

    @Scenario("inner-join-scenario-test-without-output")
    fun innerJoinScenarioWithoutOutput() {
        val counter1 = AtomicInteger(0)
        val counter2 = AtomicInteger(0)

        scenario {
            minionsCount = minionsNumber
            profile { regular(100, minionsNumber) }
        }
            .start()
            .returns<Int> { counter1.getAndIncrement() }.configure {
                name = "left-return"
            }
            .innerJoin(
                using = {
                    it.value
                },
                on = {
                    // The root step is not a singleton, hence should be repeated as much as required.
                    it.returns<Int> { counter2.getAndIncrement() }
                        .configure {
                            iterate(minionsNumber.toLong())
                            name = "right-return"
                        }
                        // A singleton step is voluntary added in the between.
                        .singletonPipe().configure {
                            name = "singleton-pipe"
                        }
                },
                having = {
                    it.value
                }
            ).configure {
                name = "inner-join"
            }
            .filterNotNull()
            .map { it.first }
            .filter { false }
            .onEach(capturedValues::add)
    }
}
