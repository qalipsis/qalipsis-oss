/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.orchestration

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.Minion
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.collections.concurrentTableOf
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.campaign.Campaign
import io.qalipsis.core.factory.campaign.CampaignLifeCycleAware
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
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
class MinionsKeeperImpl(
    private val scenarioRegistry: ScenarioRegistry,
    private val runner: Runner,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
    private val reportLiveStateRegistry: CampaignReportLiveStateRegistry,
    @Named(Executors.CAMPAIGN_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
) : MinionsKeeper, CampaignLifeCycleAware {

    private val minions: MutableMap<MinionId, MinionImpl> = ConcurrentHashMap()

    private val rootDagsOfMinions: MutableMap<MinionId, DirectedAcyclicGraphName> = ConcurrentHashMap()

    private val idleSingletonsMinions: MutableMap<ScenarioName, MutableCollection<MinionImpl>> = ConcurrentHashMap()

    private val singletonMinionsByDagId = concurrentTableOf<ScenarioName, DirectedAcyclicGraphName, MinionImpl>()

    private val dagIdsBySingletonMinionId = ConcurrentHashMap<MinionId, Pair<ScenarioName, DirectedAcyclicGraphName>>()

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val idleMinionsGauges = ConcurrentHashMap<ScenarioName, Gauge>()

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private val runningMinionsGauges = ConcurrentHashMap<ScenarioName, Gauge>()

    override suspend fun close(campaign: Campaign) {
        log.debug { "Not completed minions: $minions" }
    }

    override fun contains(minionId: MinionId) = minions.containsKey(minionId)

    override fun get(minionId: MinionId): Minion =
        requireNotNull(minions[minionId]) { "The minion $minionId does not exist" }

    override fun getSingletonMinion(scenarioName: ScenarioName, dagId: DirectedAcyclicGraphName): Minion {
        return requireNotNull(singletonMinionsByDagId[scenarioName, dagId]) { "the DAG $dagId is not a singleton DAG in the scenario $scenarioName" }
    }

    @LogInput
    override suspend fun create(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagIds: Collection<DirectedAcyclicGraphName>,
        minionId: MinionId,
    ) {
        scenarioRegistry[scenarioName]?.let { scenario ->
            // Extracts the DAG being the entry to the scenario for the minion.
            val rootDag = dagIds.asSequence().map { dagId -> scenario[dagId]!! }.firstOrNull { it.isRoot }
            val singletonMinion = dagIds.size == 1 && scenario[dagIds.first()]!!.run { isSingleton || !isUnderLoad }

            val minion = MinionImpl(
                id = minionId,
                campaignKey = campaignKey,
                scenarioName = scenarioName,
                // Only minions that are singleton or not in a root DAG should be immediately started.
                pauseAtStart = rootDag != null || singletonMinion, // Minions at the root should not be started yet.
                isSingleton = singletonMinion
            )
            minions[minionId] = minion

            if (minion.isSingleton || rootDag?.isUnderLoad == false) {
                if (singletonMinion) {
                    log.debug { "Created the singleton minion $minionId" }
                }

                val dagId = dagIds.first()
                idleSingletonsMinions.computeIfAbsent(scenarioName) { concurrentSet() } += minion
                singletonMinionsByDagId.put(scenarioName, dagId, minion)
                dagIdsBySingletonMinionId[minionId] = scenarioName to dagId
            }

            if (rootDag != null) {
                idleMinionsGauges.computeIfAbsent(minion.scenarioName) { scenario ->
                    meterRegistry.gauge(
                            scenarioName = scenario,
                            stepName = "",
                            name = "idle-minions",
                            tags = mapOf("scenario" to scenario),
                    )
                }.increment()

                rootDagsOfMinions[minionId] = rootDag.name
                eventsLogger.debug(
                    "minion.created",
                    tags = mapOf("campaign" to campaignKey, "scenario" to scenarioName, "minion" to minionId)
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
    override suspend fun scheduleMinionStart(startInstant: Instant, minionIds: Collection<MinionId>) {
        val minionsToStart = minionIds.mapNotNull { minions[it]?.takeUnless { minion -> minion.isStarted() } }
        if (minionsToStart.isNotEmpty()) {
            log.trace { "Starting ${minionsToStart.size} minions" }
            val waitingDelay = startInstant.toEpochMilli() - System.currentTimeMillis()
            if (waitingDelay > 0) {
                log.trace { "Waiting for $waitingDelay ms until start of ${minionsToStart.size} minions" }
                delay(waitingDelay)
            }
            val refMinion = minionsToStart.first()

            minionsToStart.onEach {
                it.start()
                eventsLogger.debug(
                    "minion.started",
                    tags = mapOf(
                        "campaign" to it.campaignKey,
                        "scenario" to it.scenarioName,
                        "minion" to it.id
                    )
                )
            }
            reportLiveStateRegistry.recordStartedMinion(
                refMinion.campaignKey,
                refMinion.scenarioName,
                minionsToStart.size
            )
            idleMinionsGauges.computeIfAbsent(refMinion.scenarioName) { scenario ->
                meterRegistry.gauge(
                    scenarioName = scenario,
                    stepName = "",
                    name = "idle-minions",
                    tags = mapOf("scenario" to scenario),
                )
            }.decrement(minionsToStart.size.toDouble())
            runningMinionsGauges.computeIfAbsent(refMinion.scenarioName) { scenario ->
                meterRegistry.gauge(
                    scenarioName = scenario,
                    stepName = "",
                    name = "running-minions",
                    tags = mapOf("scenario" to scenario),
                )
            }.increment(minionsToStart.size.toDouble())
        }
    }

    @LogInput
    override suspend fun restartMinion(minionId: MinionId) {
        minions[minionId]?.also { minion ->
            minion.restart(true) // Suspend the minion while it is being prepared.
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

    @LogInput(callstack = true)
    override suspend fun shutdownMinion(minionId: MinionId, interrupt: Boolean) {
        minions.remove(minionId)?.also { minion ->
            shutdownMinion(minion, interrupt)
        }
    }

    @LogInput
    override suspend fun shutdownAll() {
        minions.values.forEach { minion ->
            kotlin.runCatching {
                shutdownMinion(minion, true)
            }
        }
        minions.clear()
        idleSingletonsMinions.clear()
        singletonMinionsByDagId.clear()
        dagIdsBySingletonMinionId.clear()
        rootDagsOfMinions.clear()

        idleMinionsGauges.clear()
        runningMinionsGauges.clear()
    }

    private suspend fun shutdownMinion(minion: MinionImpl, interrupt: Boolean) {
        val minionId = minion.id
        val campaign = minion.campaignKey
        val scenario = minion.scenarioName
        log.trace { "The minion $minionId on the scenario $scenario is completed" }
        val eventsTags =
            mapOf(
                "campaign" to campaign,
                "scenario" to scenario,
                "minion" to minionId,
                "interrupt" to "$interrupt"
            )
        eventsLogger.debug("minion.completion.in-progress", tags = eventsTags)
        if (!minion.isSingleton) {
            reportLiveStateRegistry.recordCompletedMinion(campaign, scenario)
        }

        dagIdsBySingletonMinionId.remove(minionId)
            ?.let { (scenarioName, dagId) -> singletonMinionsByDagId.remove(scenarioName, dagId) }
        rootDagsOfMinions.remove(minionId)
        try {
            minion.stop(interrupt)
            eventsLogger.debug("minion.completion.done", tags = eventsTags)
        } catch (e: Exception) {
            eventsLogger.debug("minion.completion.failed", e, tags = eventsTags)
        }
    }

    companion object {
        @JvmStatic
        private var log = logger()
    }

}
