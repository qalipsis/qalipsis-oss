package io.qalipsis.core.factory.orchestration

import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
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

    private val scenarios = ConcurrentHashMap<ScenarioId, Scenario>()

    private val dags = concurrentTableOf<ScenarioId, DirectedAcyclicGraphId, DirectedAcyclicGraph>()

    override fun contains(scenarioId: ScenarioId): Boolean {
        return scenarios.keys.contains(scenarioId)
    }

    override fun get(scenarioId: ScenarioId): Scenario? {
        return scenarios[scenarioId]
    }

    override fun get(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): DirectedAcyclicGraph? {
        return dags.get(scenarioId, dagId)
    }

    override fun add(scenario: Scenario) {
        scenarios[scenario.id] = scenario
        scenario.dags.forEach {
            dags.put(scenario.id, it.id, it)
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