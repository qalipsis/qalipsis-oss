package io.evolue.core.factory.orchestration

import io.evolue.api.context.CampaignId
import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.MinionId
import io.evolue.api.context.ScenarioId
import io.evolue.api.events.EventsLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.orchestration.MinionsRegistry
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.core.annotations.LogInput
import io.evolue.core.cross.driving.feedback.EndOfCampaignFeedback
import io.evolue.core.cross.driving.feedback.FeedbackProducer
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

/**
 * Registry to keep the minions of the current factory.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinionsKeeper(
    private val scenariosKeeper: ScenariosKeeper,
    private val runner: Runner,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    private val feedbackProducer: FeedbackProducer
) : MinionsRegistry {

    private val minions: MutableMap<MinionId, MinionImpl> = ConcurrentHashMap()

    private val readySingletonsMinions: MutableMap<ScenarioId, MutableList<MinionImpl>> = ConcurrentHashMap()

    private var campaignId: CampaignId? = null

    override fun has(minionId: MinionId) = minions.containsKey(minionId)

    override fun get(minionId: MinionId) = minions[minionId]

    private val runningMinionsLatch = SuspendedCountLatch(0) {
        log.info("All the minions were executed")
        campaignId?.let {
            eventsLogger.info("end-of-campaign", null, "campaignId" to it)
            feedbackProducer.publish(EndOfCampaignFeedback(it))
        }
    }

    /**
     * Create a new Minion for the given scenario and directed acyclic graph.
     *
     * @param campaignId the ID of the campaign to execute.
     * @param scenarioId the ID of the scenario to execute.
     * @param dagId the ID of the directed acyclic graph to execute in the scenario.
     * @param minionId the ID of the minion.
     */
    @LogInput
    fun create(campaignId: CampaignId, scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId, minionId: MinionId) {
        this.campaignId = campaignId
        scenariosKeeper.getDag(scenarioId, dagId)?.let { dag ->
            log.trace("Creating minion ${minionId} for DAG ${dagId} of scenario ${scenarioId}")
            val minion = MinionImpl(campaignId, minionId, true, eventsLogger, meterRegistry)
            if (dag.singleton) {
                // All singletons are started at the time time, but before the "real" minions.
                readySingletonsMinions.computeIfAbsent(scenarioId) { mutableListOf() }.add(minion)
            } else {
                runningMinionsLatch.blockingIncrement()
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
    @LogInput
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
    @LogInput
    suspend fun startMinionAt(minionId: MinionId, instant: Instant) {
        if (minions.containsKey(minionId)) {
            val waitingDelay = instant.toEpochMilli() - System.currentTimeMillis()
            if (waitingDelay > 0) {
                log.trace("Waiting for $waitingDelay ms until start of minion $minionId")
                delay(waitingDelay)
            }
            log.trace("Starting minion $minionId")
            minions[minionId]?.apply {
                onComplete { runningMinionsLatch.decrement() }
                start()
            }
        }
    }

    companion object {
        @JvmStatic
        private var log = logger()
    }

}
