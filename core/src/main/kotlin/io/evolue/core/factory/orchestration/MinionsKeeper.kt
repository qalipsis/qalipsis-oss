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
import org.slf4j.event.Level
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

    private val minionsCountLatchesByCampaign = ConcurrentHashMap<CampaignId, SuspendedCountLatch>()

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
    @LogInput(level = Level.DEBUG)
    fun create(campaignId: CampaignId, scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId, minionId: MinionId) {
        scenariosKeeper.getDag(scenarioId, dagId)?.let { dag ->
            val runningMinionsLatch = minionsCountLatchesByCampaign.computeIfAbsent(campaignId) {
                SuspendedCountLatch(0) {
                    log.info("All the minions were executed")
                    scenariosKeeper.stopScenario(campaignId, scenarioId)
                    eventsLogger.info("end-of-campaign", null, "campaignId" to campaignId, "scenarioId" to scenarioId)
                    feedbackProducer.publish(EndOfCampaignFeedback(campaignId))
                    minionsCountLatchesByCampaign.remove(campaignId)
                }
            }

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
     * Start all the steps for a campaign and the related singleton minions.
     */
    @LogInput(level = Level.DEBUG)
    suspend fun startCampaign(campaignId: CampaignId, scenarioId: ScenarioId) {
        if (scenariosKeeper.hasScenario(scenarioId)) {
            scenariosKeeper.startScenario(campaignId, scenarioId)
            readySingletonsMinions[scenarioId]?.apply {
                forEach { minion ->
                    log.debug("Starting singleton minion ${minion.id}")
                    minion.start()
                }
                clear()
            }
        }
    }

    /**
     * Start a minion at the specified instant.
     *
     * @param minionId the ID of the minion to start.
     * @param instant the instant when the minion has to start. If the instant is already in the past, the minion starts immediately.
     */
    @LogInput
    suspend fun startMinionAt(minionId: MinionId, instant: Instant) {
        minions[minionId]?.let { minion ->
            val waitingDelay = instant.toEpochMilli() - System.currentTimeMillis()
            log.trace("Starting minion $minionId")
            val runningMinionsLatch = minionsCountLatchesByCampaign[minion.campaignId]!!
            minion.onComplete {
                runningMinionsLatch.decrement()
                minions.remove(minionId)
            }
            if (waitingDelay > 0) {
                log.trace("Waiting for $waitingDelay ms until start of minion $minionId")
                delay(waitingDelay)
            }
            minion.start()
        }
    }

    companion object {
        @JvmStatic
        private var log = logger()
    }

}
