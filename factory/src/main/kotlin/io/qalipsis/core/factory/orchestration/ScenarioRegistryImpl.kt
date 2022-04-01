package io.qalipsis.core.factory.orchestration

import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.lifetime.ProcessExitCodeSupplier
import jakarta.inject.Singleton
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of the locally supported scenarios.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class ScenarioRegistryImpl : ScenarioRegistry, ProcessExitCodeSupplier {

    private val scenarios = ConcurrentHashMap<ScenarioName, Scenario>()

    private val dags = concurrentTableOf<ScenarioName, DirectedAcyclicGraphName, DirectedAcyclicGraph>()

    override fun contains(scenarioName: ScenarioName): Boolean {
        return scenarios.keys.contains(scenarioName)
    }

    override fun get(scenarioName: ScenarioName): Scenario? {
        return scenarios[scenarioName]
    }

    override fun get(scenarioName: ScenarioName, dagId: DirectedAcyclicGraphName): DirectedAcyclicGraph? {
        return dags.get(scenarioName, dagId)
    }

    override fun add(scenario: Scenario) {
        scenarios[scenario.name] = scenario
        scenario.dags.forEach {
            dags.put(scenario.name, it.name, it)
        }
    }

    override fun all() = scenarios.values

    override suspend fun await(): Optional<Int> {
        // Returns the code 2 when no scenario was found.
        return if (scenarios.isEmpty()) {
            log.error { "No enabled scenario could be found in the classpath" }
            Optional.of(102)
        } else {
            Optional.empty()
        }
    }

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE

    private companion object {
        val log = logger()
    }

}