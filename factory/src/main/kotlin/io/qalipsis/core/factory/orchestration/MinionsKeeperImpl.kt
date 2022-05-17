package io.qalipsis.core.factory.orchestration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.Minion
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
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
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
internal class MinionsKeeperImpl(
    private val scenarioRegistry: ScenarioRegistry,
    private val runner: Runner,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @Named(Executors.ORCHESTRATION_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : MinionsKeeper {

    private val minions: MutableMap<MinionId, MinionImpl> = ConcurrentHashMap()

    private val rootDagsOfMinions: MutableMap<MinionId, DirectedAcyclicGraphName> = ConcurrentHashMap()

    private val idleSingletonsMinions: MutableMap<ScenarioName, MutableCollection<MinionImpl>> = ConcurrentHashMap()

    private val singletonMinionsByDagId = concurrentTableOf<ScenarioName, DirectedAcyclicGraphName, MinionImpl>()

    private val dagIdsBySingletonMinionId = ConcurrentHashMap<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>()

    override fun contains(minionId: MinionId) = minions.containsKey(minionId)

    override fun get(minionId: MinionId) = minions[minionId]!!

    override fun getSingletonMinion(scenarioName: ScenarioName, dagId: DirectedAcyclicGraphName): Minion {
        return singletonMinionsByDagId[scenarioName, dagId]!!
    }

    @LogInput
    override suspend fun create(
        campaignName: CampaignName,
        scenarioName: ScenarioName,
        dagIds: Collection<DirectedAcyclicGraphName>,
        minionId: MinionId
    ) {
        scenarioRegistry[scenarioName]?.let { scenario ->
            // Extracts the DAG being the entry to the scenario for the minion.
            val rootDag = dagIds.asSequence().map { dagId -> scenario[dagId]!! }.firstOrNull { it.isRoot }
            val minion = MinionImpl(
                minionId,
                campaignName,
                scenarioName,
                rootDag != null && rootDag.isUnderLoad, // Minions that have no root or are under load should not be started yet.
                rootDag?.isSingleton == true,
                meterRegistry.gauge(
                    "minion-running-steps",
                    listOf(
                        Tag.of("campaign", campaignName),
                        Tag.of("scenario", scenarioName),
                        Tag.of("minion", minionId)
                    ),
                    AtomicInteger()
                )!!
            )
            minions[minionId] = minion

            if (minion.isSingleton || rootDag?.isUnderLoad == false) {
                val dagId = dagIds.first()
                idleSingletonsMinions.computeIfAbsent(scenarioName) { concurrentSet() } += minion
                singletonMinionsByDagId.put(scenarioName, dagId, minion)
                dagIdsBySingletonMinionId[minionId] = scenarioName to dagId
                minion.onComplete {
                    singletonMinionsByDagId.remove(scenarioName, dagId)
                    dagIdsBySingletonMinionId.remove(minionId)
                }
            } else {
                // Removes the minion from the registries when complete.
                minion.onComplete {
                    minions.remove(minionId)
                }
            }

            if (rootDag != null) {
                rootDagsOfMinions[minionId] = rootDag.name
                eventsLogger.info(
                    "minion.created",
                    tags = mapOf("campaign" to campaignName, "scenario" to scenarioName, "minion" to minionId)
                )
                // When the minion is not under load or executes the root DAG locally, it is started and kept idle.
                // Otherwise, it will be the responsibility of the "DAG input step" to perform the execution when
                // a step context will be received for that minion.
                coroutineScope.launch { runner.run(minion, rootDag) }
                log.trace { "Minion $minionId for DAG ${rootDag.name} of scenario $scenarioName is ready to start and idle" }
            }
        }
    }

    @LogInputAndOutput(level = Level.DEBUG)
    override suspend fun startSingletons(scenarioName: ScenarioName) {
        idleSingletonsMinions.remove(scenarioName)?.forEach { minion ->
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
            reportLiveStateRegistry.recordStartedMinion(minion.campaignName, minion.scenarioName, 1)
            eventsLogger.info(
                "minion.started",
                tags = mapOf(
                    "campaign" to minion.campaignName,
                    "scenario" to minion.scenarioName,
                    "minion" to minion.id
                )
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
                    val scenarioName = minion.scenarioName
                    log.trace { "Minion $minionId is restarting its execution from scratch by the DAG $rootDagId of scenario $scenarioName" }
                    runner.run(minion, scenarioRegistry[scenarioName]!![rootDagId]!!)
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
            mapOf("campaign" to minion.campaignName, "scenario" to minion.scenarioName, "minion" to minionId)
        eventsLogger.info("minion.cancellation.started", tags = eventsTags)
        dagIdsBySingletonMinionId.remove(minionId)
            ?.let { (scenarioName, dagId) -> singletonMinionsByDagId.remove(scenarioName, dagId) }
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
