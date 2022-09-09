package io.qalipsis.runtime.deployments

import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.executionprofile.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.blackHole
import io.qalipsis.api.steps.returns

object DeploymentTestScenario {

    @Scenario
    fun scenario() {
        scenario("deployment-test") {
            minionsCount = 10_000
            profile { regular(1000, 2000) }
        }.start()
            .returns(Unit)
            .blackHole()
    }
}