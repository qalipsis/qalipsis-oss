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

package io.qalipsis.runtime.deployments

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.CompletionMode.GRACEFUL
import io.qalipsis.api.executionprofile.stages
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.delay
import io.qalipsis.api.steps.execute
import io.qalipsis.api.steps.filterNotNull
import io.qalipsis.api.steps.flatten
import io.qalipsis.api.steps.innerJoin
import io.qalipsis.api.steps.pipe
import io.qalipsis.api.steps.returns
import io.qalipsis.api.steps.verify
import java.util.concurrent.atomic.AtomicInteger

object DeploymentTestScenario {

    @Scenario("deployment-test")
    fun deploymentTest() {
        scenario {
            minionsCount = 500
        }.start()
            .returns(Unit)
            .delay(200)
            .blackHole()
    }

    @Scenario("deployment-test-with-failures")
    fun deploymentTestWithFailures() {
        scenario {
            minionsCount = 500
        }.start()
            .returns(Unit)
            .delay(200)
            .execute<Unit, Unit> {
                throw RuntimeException("There is a failure")
            }
            .blackHole()
    }

    @Scenario("deployment-test-with-singleton")
    fun deploymentTestWithSingleton() {
        val minions = 500
        val counter = AtomicInteger(0)
        scenario {
            minionsCount = minions
        }.start()
            .returns { counter.incrementAndGet() }
            .pipe()
            .innerJoin()
            .using { "${it.value}" }
            .on {
                it.returns { (1..minions).toList() }
                    .flatten()
            }
            .having { "${it.value}" }
            .filterNotNull()
            .verify {
                assertThat(it.first).isEqualTo(it.second)
            }
    }

    @Scenario("deployment-test-with-repeated-minions-in-stages")
    fun deploymentTestWithRepeatedMinionsInStages() {
        val minions = 5_000
        scenario {
            minionsCount = minions
            profile {
                stages(GRACEFUL) {
                    stage(
                        minionsPercentage = 50.0,
                        rampUpDurationMs = 1000,
                        totalDurationMs = 2000,
                        resolutionMs = 100
                    )
                    stage(
                        minionsPercentage = 20.0,
                        rampUpDurationMs = 500,
                        totalDurationMs = 800,
                        resolutionMs = 50
                    )
                    stage(
                        minionsPercentage = 30.0,
                        rampUpDurationMs = 1000,
                        totalDurationMs = 2000,
                        resolutionMs = 100
                    )
                }
            }
        }.start()
            .returns<Unit> { }
            .delay(300)
    }
}