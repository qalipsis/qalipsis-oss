package io.qalipsis.core.factory.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.Minion
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.collections.concurrentTableOf
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.event.Level
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Registry to keep the minions of the current factory.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinionsKeeperImpl(
    private val scenarioRegistry: ScenarioRegistry,
    private val runner: Runner,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : MinionsKeeper {

    private val minions: MutableMap<MinionId, MinionImpl> = ConcurrentHashMap()

    private val rootDagsOfMinions: MutableMap<MinionId, DirectedAcyclicGraphId> = ConcurrentHashMap()

    private val idleSingletonsMinions: MutableMap<ScenarioId, MutableCollection<MinionImpl>> = ConcurrentHashMap()

    private val singletonMinionsByDagId = concurrentTableOf<ScenarioId, DirectedAcyclicGraphId, MinionImpl>()

    private val dagIdsBySingletonMinionId = ConcurrentHashMap<MinionId, Pair<ScenarioId, DirectedAcyclicGraphId>>()

    override fun contains(minionId: MinionId) = minions.containsKey(minionId)

    override fun get(minionId: MinionId) = minions[minionId]!!

    override fun getSingletonMinion(scenarioId: ScenarioId, dagId: DirectedAcyclicGraphId): Minion {
        return singletonMinionsByDagId[scenarioId, dagId]!!
    }

    @LogInput(level = Level.DEBUG)
    override suspend fun create(
        campaignId: CampaignId, scenarioId: ScenarioId, dagIds: Collection<DirectedAcyclicGraphId>, minionId: MinionId
    ) {
        scenarioRegistry[scenarioId]?.let { scenario ->
            // Extracts the DAG being the entry to the scenario for the minion.
            val rootDag = dagIds.asSequence().map { dagId -> scenario[dagId]!! }.firstOrNull { it.isRoot }
            val minion = MinionImpl(
                minionId,
                campaignId,
                scenarioId,
                rootDag != null && rootDag.isUnderLoad, // Minions that have no root or are under load should not be started yet.
                rootDag?.isSingleton == true,
                meterRegistry.gauge(
                    "minion-running-steps",
                    listOf(Tag.of("campaign", campaignId), Tag.of("scenario", scenarioId), Tag.of("minion", minionId)),
                    AtomicInteger()
                )
            )
            minions[minionId] = minion

            if (minion.isSingleton || rootDag?.isUnderLoad == false) {
                val dagId = dagIds.first()
                idleSingletonsMinions.computeIfAbsent(scenarioId) { concurrentSet() } += minion
                singletonMinionsByDagId.put(scenarioId, dagId, minion)
                dagIdsBySingletonMinionId[minionId] = scenarioId to dagId
                minion.onComplete {
                    singletonMinionsByDagId.remove(scenarioId, dagId)
                    dagIdsBySingletonMinionId.remove(minionId)
                }
            } else {
                // Removes the minion from the registries when complete.
                minion.onComplete {
                    minions.remove(minionId)
                }
            }

            if (rootDag != null) {
                rootDagsOfMinions[minionId] = rootDag.id
                eventsLogger.info(
                    "minion.created",
                    tags = mapOf("campaign" to campaignId, "scenario" to scenarioId, "minion" to minionId)
                )
                // When the minion is not under load or executes the root DAG locally, it is started and kept idle.
                // Otherwise, it will be the responsibility of the "DAG input step" to perform the execution when
                // a step context will be received for that minion.
                coroutineScope.launch { runner.run(minion, rootDag) }
                log.trace { "Minion $minionId for DAG ${rootDag.id} of scenario $scenarioId is ready to start and idle" }
            }
        }
    }

    @LogInputAndOutput(level = Level.DEBUG)
    override suspend fun startSingletons(scenarioId: ScenarioId) {
        idleSingletonsMinions.remove(scenarioId)?.forEach { minion ->
            log.debug { "Starting singleton minion ${minion.id}" }
            minion.start()
        }
    }

    @LogInput
    override suspend fun scheduleMinionStart(minionId: MinionId, instant: Instant) {
        minions[minionId]?.takeUnless { it.isStarted() }?.let { minion ->
            log.trace { "Starting minion $minionId" }
            val waitingDelay = instant.toEpochMilli() - System.currentTimeMillis()
            if (waitingDelay > 0) {
                log.trace { "Waiting for $waitingDelay ms until start of minion $minionId" }
                delay(waitingDelay)
            }
            minion.start()
            reportLiveStateRegistry.recordStartedMinion(minion.campaignId, minion.scenarioId, 1)
            eventsLogger.info(
                "minion.started",
                tags = mapOf("campaign" to minion.campaignId, "scenario" to minion.scenarioId, "minion" to minion.id)
            )
        }
    }

    @LogInput
    override suspend fun restartMinion(minionId: MinionId) {
        minions[minionId]?.also { minion ->
            runCatching {
                minion.cancel()
            }
            minion.reset(true)
            rootDagsOfMinions[minionId]?.let { rootDagId ->
                coroutineScope.launch {
                    log.trace { "Minion $minionId is restarting its execution from scratch by the DAG $rootDagId of scenario ${minion.scenarioId}" }
                    runner.run(minion, scenarioRegistry[minion.scenarioId]!![rootDagId]!!)
                }
            }
            minion.start()
        }
    }

    @LogInput
    override suspend fun shutdownMinion(minionId: MinionId) {
        minions.remove(minionId)?.also { minion ->
            shutdownMinion(minion)
        }
    }

    @LogInput
    override suspend fun shutdownAll() {
        minions.values.forEach { minion ->
            kotlin.runCatching {
                shutdownMinion(minion)
            }
        }
        minions.clear()
        idleSingletonsMinions.clear()
        singletonMinionsByDagId.clear()
        dagIdsBySingletonMinionId.clear()
        rootDagsOfMinions.clear()
    }

    private suspend fun shutdownMinion(minion: MinionImpl) {
        val minionId = minion.id
        val eventsTags =
            mapOf("campaign" to minion.campaignId, "scenario" to minion.scenarioId, "minion" to minionId)
        eventsLogger.info("minion.cancellation.started", tags = eventsTags)
        dagIdsBySingletonMinionId.remove(minionId)
            ?.let { (scenarioId, dagId) -> singletonMinionsByDagId.remove(scenarioId, dagId) }
        rootDagsOfMinions.remove(minionId)
        try {
            minion.cancel()
            eventsLogger.info("minion.cancellation.complete", tags = eventsTags)
        } catch (e: Exception) {
            eventsLogger.info("minion.cancellation.complete", e, tags = eventsTags)
        }
    }

    companion object {
        @JvmStatic
        private var log = logger()
    }

}
