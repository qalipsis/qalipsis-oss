package io.evolue.core.factory.orchestration

import com.google.common.annotations.VisibleForTesting
import io.evolue.api.context.CampaignId
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.MinionId
import io.evolue.api.context.ScenarioId
import io.evolue.api.events.EventLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.MinionsRegistry
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry to keep the minions of the current factory.
 *
 * @author Eric Jessé
 */
internal class MinionsKeeper(
    private val scenariosKeeper: ScenariosKeeper,
    private val runner: Runner,
    private val eventLogger: EventLogger,
    private val meterRegistry: MeterRegistry
) : MinionsRegistry {

    @VisibleForTesting
    val minions: MutableMap<MinionId, MinionImpl> = ConcurrentHashMap()

    @VisibleForTesting
    val readySingletonsMinions: MutableMap<ScenarioId, MutableList<MinionImpl>> = ConcurrentHashMap()

    override fun has(minionId: MinionId) = minions.containsKey(minionId)

    override fun get(minionId: MinionId) = minions[minionId]

    /**
     * Create a new Minion for the given scenario and directed acyclic graph.
     *
     * @param campaignId the ID of the campaign to execute.
     * @param scenarioId the ID of the scenario to execute.
     * @param dagId the ID of the directed acyclic graph to execute in the scenario.
     * @param minionId the ID of the minion.
     */
    fun create(campaignId: CampaignId, scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId, minionId: MinionId) {
        scenariosKeeper.getDag(scenarioId, dagId)?.let { dag ->
            val minion = MinionImpl(campaignId, minionId, true, eventLogger, meterRegistry)
            if (dag.singleton) {
                // All singletons are started at the time time, but before the "real" minions.
                readySingletonsMinions.computeIfAbsent(scenarioId) { mutableListOf() }.add(minion)
            } else {
                minions[minionId] = minion
            }

            // Runs the minion, which will be idle until it is called to start.
            GlobalScope.launch {
                runner.run(minion, dag)
            }
        }
    }

    /**
     * Start all the singleton minions attached to the given scenario.
     */
    suspend fun startSingletons(scenarioId: ScenarioId) {
        readySingletonsMinions[scenarioId]?.forEach { minion ->
            minion.start()
        }
        readySingletonsMinions.clear()
    }

    /**
     * Start a minion at the specified instant.
     *
     * @param minionId the ID of the minion to start.
     * @param instant the instant when the minion has to start. If the instant is already in the past, the minion starts immediately.
     */
    suspend fun startMinionAt(minionId: MinionId, instant: Instant) {
        if (minions.containsKey(minionId)) {
            val waitingDelay = instant.toEpochMilli() - System.currentTimeMillis()
            if (waitingDelay > 0) {
                log.trace("Waiting for $waitingDelay ms until start of minion $minionId")
                delay(waitingDelay)
            }
            log.trace("Starting minion $minionId")
            minions[minionId]?.start()
        }
    }

    companion object {
        @JvmStatic
        private var log = logger()
    }

}