package io.qalipsis.core.factories.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.orchestration.Scenario
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.orchestration.factories.MinionsKeeper
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.event.Level
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry to keep the minions of the current factory.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinionsKeeperImpl(
    private val scenariosRegistry: ScenariosRegistry,
    private val runner: Runner,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    private val campaignStateKeeper: CampaignStateKeeper,
    private val feedbackProducer: FeedbackProducer,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : MinionsKeeper {

    private val minions: MutableMap<MinionId, MutableCollection<MinionImpl>> = ConcurrentHashMap()

    private val readySingletonsMinions: MutableMap<ScenarioId, MutableCollection<MinionImpl>> = ConcurrentHashMap()

    private val minionsCountLatchesByCampaign = ConcurrentHashMap<CampaignId, SuspendedCountLatch>()

    private val singletonMinionsByDagId = ConcurrentHashMap<DirectedAcyclicGraphId, Minion>()

    override fun has(minionId: MinionId) = minions.containsKey(minionId)

    override fun get(minionId: MinionId) = minions[minionId] ?: emptyList()

    override fun getSingletonMinion(dagId: DirectedAcyclicGraphId): Minion {
        return singletonMinionsByDagId[dagId]!!
    }

    @LogInputAndOutput(level = Level.DEBUG)
    override suspend fun create(
        campaignId: CampaignId, scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId,
        minionId: MinionId
    ) {
        scenariosRegistry[scenarioId]?.let { scenario ->
            scenario[dagId]?.let { dag ->
                val runningMinionsLatch = minionsCountLatchesByCampaign.computeIfAbsent(campaignId) {
                    SuspendedCountLatch(0) { onCampaignComplete(campaignId, scenario) }
                }

                log.trace { "Creating minion $minionId for DAG $dagId of scenario $scenarioId" }
                // Only minions for singleton and DAGs under load are started and scheduled.
                // The others will be used on demand.
                val minion = MinionImpl(minionId, campaignId, scenarioId, dagId, true, eventsLogger, meterRegistry)
                when {
                    dag.isUnderLoad && !dag.isSingleton -> {
                        runningMinionsLatch.increment()
                        // There can be the same minion on several DAGs, each "instance" of the same
                        // minion is considered separately.
                        minions.computeIfAbsent(minionId) { concurrentSet() }.add(minion)

                        // Remove the minion from the registries when complete.
                        minion.onComplete {
                            minions[minionId]?.remove(minion)
                        }
                    }
                    !singletonMinionsByDagId.containsKey(dagId) -> {
                        // All singletons are started at the same time, prior to the "real" minions.
                        readySingletonsMinions.computeIfAbsent(scenarioId) { concurrentSet() }.add(minion)
                        singletonMinionsByDagId[dagId] = minion

                        // Remove the minion from the registries when complete.
                        minion.onComplete {
                            readySingletonsMinions[scenarioId]?.remove(minion)
                            singletonMinionsByDagId.remove(dagId)
                        }
                    }
                }

                // The minion is ready to start but remains idle until the call to startCampaign().
                coroutineScope.launch { runner.run(minion, dag) }
                log.trace { "Minion $minionId for DAG $dagId of scenario $scenarioId is ready to start and idle" }
            }
        }
    }

    /**
     * Actions to trigger when the campaign completes.
     */
    @LogInputAndOutput(level = Level.DEBUG)
    protected suspend fun onCampaignComplete(
        campaignId: CampaignId,
        scenario: Scenario
    ) {
        log.info { "All the minions were executed for campaign $campaignId of scenario ${scenario.id}" }
        scenario.stop(campaignId)
        eventsLogger.info(
            "minions-keeper.campaign.complete", null, Instant.now(), "campaign" to campaignId,
            "scenarioId" to scenario.id
        )
        campaignStateKeeper.complete(campaignId, scenario.id)
        feedbackProducer.publish(EndOfCampaignFeedback(campaignId, scenario.id))
        minionsCountLatchesByCampaign.remove(campaignId)
        // Removes all the meters.
        meterRegistry.clear()
    }


    @LogInputAndOutput(level = Level.DEBUG)
    override suspend fun startCampaign(campaignId: CampaignId, scenarioId: ScenarioId) {
        if (scenarioId in scenariosRegistry) {
            log.info { "Starting campaign $campaignId on scenario $scenarioId" }
            campaignStateKeeper.start(campaignId, scenarioId)
            scenariosRegistry[scenarioId]!!.start(campaignId)
            readySingletonsMinions[scenarioId]?.apply {
                forEach { minion ->
                    log.debug { "Starting singleton minion ${minion.id}" }
                    minion.start()
                }
                clear()
            }
        }
    }

    @LogInput
    override suspend fun startMinionAt(minionId: MinionId, instant: Instant) {
        minions[minionId]?.let { minionsWithId ->
            val (campaignId, scenarioId) = minionsWithId.first().let { it.campaignId to it.scenarioId }
            log.trace { "Starting minion $minionId" }
            val waitingDelay = instant.toEpochMilli() - System.currentTimeMillis()
            val runningMinionsLatch = minionsCountLatchesByCampaign[campaignId]!!
            minionsWithId.forEach { minion ->
                minion.onComplete {
                    campaignStateKeeper.recordCompletedMinion(campaignId, scenarioId)
                    runningMinionsLatch.decrement()
                    minionsWithId.remove(minion)
                    if (minionsWithId.isEmpty()) {
                        minions.remove(minionId)
                    }
                }
            }
            if (waitingDelay > 0) {
                log.trace { "Waiting for $waitingDelay ms until start of minion $minionId" }
                delay(waitingDelay)
            }
            minionsWithId.forEach {
                it.start()
            }
            with(minionsWithId) {
                campaignStateKeeper.recordStartedMinion(campaignId, scenarioId, size)
            }
        }
    }

    companion object {
        @JvmStatic
        private var log = logger()
    }

}
