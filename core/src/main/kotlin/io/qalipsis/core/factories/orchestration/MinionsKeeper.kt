package io.qalipsis.core.factories.orchestration

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.factories.MinionsRegistry
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.cross.feedbacks.EndOfCampaignFeedback
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.event.Level
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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

    private val minions: MutableMap<MinionId, MutableList<MinionImpl>> = ConcurrentHashMap()

    private val readySingletonsMinions: MutableMap<ScenarioId, MutableList<MinionImpl>> = ConcurrentHashMap()

    private val minionsCountLatchesByCampaign = ConcurrentHashMap<CampaignId, SuspendedCountLatch>()

    override fun has(minionId: MinionId) = minions.containsKey(minionId)

    override fun get(minionId: MinionId) = minions[minionId] ?: emptyList()

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

            log.trace("Creating minion $minionId for DAG $dagId of scenario $scenarioId")
            val minion = MinionImpl(minionId, campaignId, dagId, true, eventsLogger, meterRegistry)
            if (dag.singleton) {
                // All singletons are started at the same time, prior to the "real" minions.
                readySingletonsMinions.computeIfAbsent(scenarioId) { mutableListOf() }.add(minion)
            } else {
                runningMinionsLatch.blockingIncrement()
                // There can be the same minion on several DAGs, each "instance" of the same
                // minion is considered separately.
                minions.computeIfAbsent(minionId) { CopyOnWriteArrayList() }.add(minion)
            }

            // Runs the minion, which will be idle until it the call to startCampaign().
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
        minions[minionId]?.let { minionsWithId ->
            val waitingDelay = instant.toEpochMilli() - System.currentTimeMillis()
            log.trace("Starting minion $minionId")
            val runningMinionsLatch = minionsCountLatchesByCampaign[minionsWithId.first().campaignId]!!
            minionsWithId.forEach { minion ->
                minion.onComplete {
                    runningMinionsLatch.decrement()
                    minionsWithId.remove(minion)
                    if (minionsWithId.isEmpty()) {
                        minions.remove(minionId)
                    }
                }
            }
            if (waitingDelay > 0) {
                log.trace("Waiting for $waitingDelay ms until start of minion $minionId")
                delay(waitingDelay)
            }
            minionsWithId.forEach {
                it.start()
            }
        }
    }

    companion object {
        @JvmStatic
        private var log = logger()
    }

}
