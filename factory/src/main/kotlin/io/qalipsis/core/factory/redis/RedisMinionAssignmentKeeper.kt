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

package io.qalipsis.core.factory.redis

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import io.aerisconsulting.catadioptre.KTestable
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.ValueScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines.RedisHashCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisKeyCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisScriptingCoroutinesCommands
import io.lettuce.core.api.coroutines.RedisSetCoroutinesCommands
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
import io.qalipsis.core.campaigns.FactoryScenarioAssignment
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.CampaignCompletionState
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.redis.RedisUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import kotlinx.coroutines.withTimeout

@ExperimentalLettuceCoroutinesApi
@Singleton
@Requirements(
    Requires(beans = [StatefulRedisConnection::class]),
    Requires(env = [ExecutionEnvironments.FACTORY])
)
internal class RedisMinionAssignmentKeeper(
    private val factoryConfiguration: FactoryConfiguration,
    private val communicationChannelConfiguration: CommunicationChannelConfiguration,
    private val redisScriptingCommands: RedisScriptingCoroutinesCommands<String, String>,
    private val redisSetCommands: RedisSetCoroutinesCommands<String, String>,
    private val redisHashCommands: RedisHashCoroutinesCommands<String, String>,
    private val redisKeyCommands: RedisKeyCoroutinesCommands<String, String>,
    private val localAssignmentStore: LocalAssignmentStore
) : MinionAssignmentKeeper {

    private val keysPrefix = RedisUtils.buildKeysPrefixForTenant(factoryConfiguration.tenant)

    /**
     * Script to register the minions.
     */
    private lateinit var minionRegistrationScript: ByteArray

    /**
     * Script to assign the minions.
     */
    private lateinit var minionAssignmentScript: ByteArray

    /**
     * Script to process the completion of a minions on DAGs.
     */
    private lateinit var minionCompletionScript: ByteArray

    /**
     * Script to fetch the factories channels to forward the execution contexts.
     */
    private lateinit var minionFetchFactoryChannelScript: ByteArray

    /**
     * SHA key of the script to register the minions.
     */
    private var minionRegistrationScriptSha: String = "311bdb5a9b1f6a8bba77cdedfc2a1b9c216193fc"

    /**
     * SHA key of the script to assign the minions.
     */
    private var minionAssignmentScriptSha: String = "60dcbcb796bbec38ef5cad1814b6de8967671ce5"

    /**
     * SHA key of the script to process the completion of a minions on DAGs.
     */
    private var minionCompletionScriptSha: String = "a0bf608703e22bb7614ff83fe9a862f50febb1ea"

    /**
     * SHA key of the script to fetch the factories channels to forward the execution contexts.
     */
    private var minionFetchFactoryChannelScriptSha: String = "1842437f4e1b3a2942b3fcdc1100f44a86b29c83"

    /**
     * Maximal counts of minions that can be assigned to each scenario.
     */
    @KTestable
    private val maxMinionsCountsByScenario = mutableMapOf<ScenarioName, Int>()

    @PostConstruct
    fun init() {
        minionAssignmentScript = RedisUtils.loadScript("/redis/minion-assignment.lua")
        minionCompletionScript = RedisUtils.loadScript("/redis/minion-dag-completion.lua")
        minionRegistrationScript = RedisUtils.loadScript("/redis/register-minions.lua")
        minionFetchFactoryChannelScript = RedisUtils.loadScript("/redis/minion-get-factories-channels.lua")
    }

    @LogInputAndOutput
    override suspend fun assignFactoryDags(
        campaignKey: CampaignKey,
        assignments: Collection<FactoryScenarioAssignment>
    ) = assignFactoryDags(campaignKey, assignments, factoryConfiguration.nodeId)

    /**
     * Factory-configurable version of [assignFactoryDags] for testing purpose.
     */
    private suspend fun assignFactoryDags(
        campaignKey: CampaignKey,
        assignments: Collection<FactoryScenarioAssignment>,
        factoryNodeId: String
    ) {
        localAssignmentStore.reset()
        maxMinionsCountsByScenario.clear()
        assignments.forEach { (scenarioName, dagIds, maxMinionsCount) ->
            maxMinionsCountsByScenario[scenarioName] = maxMinionsCount
            val keyForFactoryAssignment = buildRedisKeyPrefix(campaignKey, scenarioName) + factoryNodeId
            redisSetCommands.sadd(keyForFactoryAssignment, *dagIds.toTypedArray())
        }
    }

    /**
     * Creates the common prefix of all the keys used in the assignment process.
     */
    private fun buildRedisKeyPrefix(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ) = buildRedisKeyPrefix(campaignKey) + "$scenarioName:"

    /**
     * Creates the common prefix of all the keys used in the assignment process.
     *
     * A Hash tag is used in the key prefix to locate all the values related to the same campaign.
     */
    private fun buildRedisKeyPrefix(
        campaignKey: CampaignKey
    ) = "${keysPrefix}{$campaignKey}-assignment:"

    @LogInputAndOutput
    override suspend fun registerMinionsToAssign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        dagIds: Collection<DirectedAcyclicGraphName>,
        minionIds: Collection<MinionId>,
        underLoad: Boolean
    ) {
        val campaignRelatedKeyPrefix = buildRedisKeyPrefix(campaignKey)
        val scenarioRelatedKeyPrefix = buildRedisKeyPrefix(campaignKey, scenarioName)

        // Create the set with the minions to register.
        val minionsSet = scenarioRelatedKeyPrefix + Math.random()
        redisSetCommands.sadd(minionsSet, *minionIds.toTypedArray())

        val unassignedMinionsSetKey = scenarioRelatedKeyPrefix + UNASSIGNED_MINIONS
        val underLoadMinionsSetKey = scenarioRelatedKeyPrefix + UNDERLOAD_MINIONS
        val keyPrefixForUnassignedDags = scenarioRelatedKeyPrefix + MINION_UNASSIGNED_DAGS_PREFIX
        val countersHashKey = campaignRelatedKeyPrefix + CAMPAIGN_COUNTERS
        val singletonsHashKey = campaignRelatedKeyPrefix + SINGLETON_REGISTRY
        executeRegistration(
            minionsSet,
            unassignedMinionsSetKey,
            underLoadMinionsSetKey,
            keyPrefixForUnassignedDags,
            countersHashKey,
            singletonsHashKey,
            scenarioName,
            dagIds,
            underLoad
        )
    }

    /**
     * Executes the script to complete the DAGS of a minion from the SHA key of the script.
     * If the SHA key is not valid, the script is first loaded into Redis before a new attempt.
     */
    @Suppress("kotlin:S107")
    private suspend fun executeRegistration(
        minionsSetKey: String,
        unassignedMinionsSetKey: String,
        underLoadMinionsSetKey: String,
        keyPrefixForUnassignedDags: String,
        countersHashKey: String,
        singletonsHashKey: String,
        scenarioName: String,
        dagIds: Collection<String>,
        underLoad: Boolean
    ): Boolean {
        return try {
            redisScriptingCommands.evalsha(
                minionRegistrationScriptSha, ScriptOutputType.BOOLEAN,
                arrayOf(
                    minionsSetKey,
                    unassignedMinionsSetKey,
                    underLoadMinionsSetKey,
                    keyPrefixForUnassignedDags,
                    countersHashKey,
                    singletonsHashKey
                ),
                scenarioName, dagIds.joinToString(separator = ","), underLoad.toString()
            )!!
        } catch (e: RedisNoScriptException) {
            minionRegistrationScriptSha = redisScriptingCommands.scriptLoad(minionRegistrationScript)!!
            log.debug { "Registration script was loaded with SHA $minionRegistrationScriptSha" }
            executeRegistration(
                minionsSetKey,
                unassignedMinionsSetKey,
                underLoadMinionsSetKey,
                keyPrefixForUnassignedDags,
                countersHashKey,
                singletonsHashKey,
                scenarioName,
                dagIds,
                underLoad
            )
        }
    }

    override suspend fun getIdsOfMinionsUnderLoad(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ): Collection<MinionId> {
        val unassignedMinionsSetKey = buildRedisKeyPrefix(campaignKey, scenarioName) + UNDERLOAD_MINIONS
        val minionsIds = mutableListOf<MinionId>()
        var scanResult: ValueScanCursor<String>? = null
        do {
            scanResult = scanResult?.let { redisSetCommands.sscan(unassignedMinionsSetKey, it) }
                ?: redisSetCommands.sscan(unassignedMinionsSetKey)

            scanResult?.let { minionsIds += it.values }
        } while (scanResult?.isFinished != true)
        return minionsIds
    }

    override suspend fun completeUnassignedMinionsRegistration(campaignKey: CampaignKey, scenarioName: ScenarioName) {
        // Updates the number active scenarios in the campaign.
        val countersHashKey = buildRedisKeyPrefix(campaignKey) + CAMPAIGN_COUNTERS
        redisHashCommands.hincrby(countersHashKey, SCENARIOS_COUNT_FIELD, 1)
    }

    @LogInputAndOutput
    override suspend fun assign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName
    ) = assign(
        campaignKey,
        scenarioName,
        factoryConfiguration.nodeId,
        communicationChannelConfiguration.unicastChannel,
        maxMinionsCountsByScenario[scenarioName]!!
    )

    /**
     * Factory-configurable version of [assign] for testing purpose.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun assign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        factoryNodeId: String,
        factoryChannelName: String,
        maximalMinionsCount: Int
    ): Map<MinionId, Collection<DirectedAcyclicGraphName>> {
        val allKeysPrefix = buildRedisKeyPrefix(campaignKey, scenarioName)
        val keyForFactoryAssignment = allKeysPrefix + factoryNodeId
        val keyPrefixForUnassignedMinions = allKeysPrefix + UNASSIGNED_MINIONS
        val keyPrefixForUnassignedDags = allKeysPrefix + MINION_UNASSIGNED_DAGS_PREFIX
        val keyPrefixForAssignedDags = allKeysPrefix + MINION_ASSIGNED_DAGS_PREFIX

        var evaluatedMinionsCount: Long
        val minionsUnderLoad = getIdsOfMinionsUnderLoad(campaignKey, scenarioName).toSet()

        val assignments = mutableMapOf<String, List<String>>()
        var assignedUnderLoad = 0
        try {
            withTimeout(factoryConfiguration.assignment.timeout.toMillis()) {
                val evaluationBatchSize = "${factoryConfiguration.assignment.evaluationBatchSize}"
                do {
                    val assignedMinions = executeAssignment(
                        keyPrefixForUnassignedMinions, keyForFactoryAssignment,
                        factoryChannelName, evaluationBatchSize, keyPrefixForUnassignedDags, keyPrefixForAssignedDags,
                        "${maximalMinionsCount - assignedUnderLoad}"
                    )
                    // Number of minions that were evaluated for assignment. When it is 0, it means that all the minions
                    // were assigned.
                    evaluatedMinionsCount = assignedMinions[1] as Long

                    // Actual assignments to add to the local factory, identifier by a succession of minion IDs / list of DAGs.
                    val newlyAssignedMinions = (assignedMinions[3] as List<*>).windowed(2, 2).map {
                        val minionId = it[0] as String
                        val dags = it[1] as List<String>
                        minionId to dags
                    }.toMap()

                    // Counts the assigned under load to respect the limit, the singletons being ignored here.
                    assignedUnderLoad += newlyAssignedMinions.keys.intersect(minionsUnderLoad).size

                    assignments += newlyAssignedMinions
                } while (evaluatedMinionsCount > 0 && assignedUnderLoad < maximalMinionsCount)
            }
        } finally {
            redisKeyCommands.unlink(keyForFactoryAssignment)
        }
        if (log.isDebugEnabled) {
            log.debug { "${assignments.size} minions assigned to factory $factoryNodeId for campaign $campaignKey and scenario $scenarioName" }
        } else if (log.isTraceEnabled) {
            log.trace { "Assignment of factory $factoryNodeId for campaign $campaignKey and scenario $scenarioName (${assignments.size} minions assigned): $assignments" }
        }

        localAssignmentStore.save(scenarioName, assignments)
        return assignments
    }

    /**
     * Executes the script to assign the minions from the SHA key of the script.
     * If the SHA key is not valid, the script is first loaded into Redis before a new attempt.
     */
    private suspend fun executeAssignment(
        keyPrefixForUnassignedMinions: String,
        keyForFactoryAssignment: String,
        factoryChannelName: String,
        evaluationBatchSize: String,
        keyPrefixForUnassignedDags: String,
        keyPrefixForAssignedDags: String,
        maximalMinionsCount: String
    ): List<*> {
        return try {
            redisScriptingCommands.evalsha(
                minionAssignmentScriptSha, ScriptOutputType.MULTI,
                arrayOf(keyPrefixForUnassignedMinions, keyForFactoryAssignment),

                factoryChannelName,
                evaluationBatchSize,
                keyPrefixForUnassignedDags,
                keyPrefixForAssignedDags,
                maximalMinionsCount
            )!!
        } catch (e: RedisNoScriptException) {
            minionAssignmentScriptSha = redisScriptingCommands.scriptLoad(minionAssignmentScript)!!
            log.debug { "Assignment script was loaded with SHA $minionAssignmentScriptSha" }
            executeAssignment(
                keyPrefixForUnassignedMinions,
                keyForFactoryAssignment,
                factoryChannelName,
                evaluationBatchSize,
                keyPrefixForUnassignedDags,
                keyPrefixForAssignedDags,
                maximalMinionsCount
            )
        }
    }

    @LogInputAndOutput
    override suspend fun executionComplete(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphName>
    ): CampaignCompletionState {
        val singletonsHashKey = buildRedisKeyPrefix(campaignKey) + SINGLETON_REGISTRY
        val countersHashKey = buildRedisKeyPrefix(campaignKey) + CAMPAIGN_COUNTERS
        val keyForAssignedDags =
            buildRedisKeyPrefix(campaignKey, scenarioName) + MINION_ASSIGNED_DAGS_PREFIX + minionId
        val completionsFlags =
            executeCompletion(
                singletonsHashKey,
                countersHashKey,
                keyForAssignedDags,
                scenarioName,
                minionId,
                dagIds.size
            )
        val state = CampaignCompletionState()
        state.minionComplete = (completionsFlags[1] as Number).toInt() > 0
        state.scenarioComplete = (completionsFlags[3] as Number).toInt() > 0
        state.campaignComplete = (completionsFlags[5] as Number).toInt() > 0
        if (state.campaignComplete) {
            cleanCampaignKeys(campaignKey)
        }
        log.trace { "$state" }
        localAssignmentStore.reset()
        return state
    }

    override suspend fun getFactoriesChannels(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionIds: Collection<MinionId>,
        dagsIds: Collection<DirectedAcyclicGraphName>
    ): Table<MinionId, DirectedAcyclicGraphName, String> {
        val keyPrefixForAssignedDags = buildRedisKeyPrefix(campaignKey, scenarioName) + MINION_ASSIGNED_DAGS_PREFIX
        val dagsAsList = dagsIds.toList()
        val factoriesChannels = executeFetchFactoryChannels(keyPrefixForAssignedDags, minionIds, dagsAsList)
        val result = HashBasedTable.create<MinionId, DirectedAcyclicGraphName, String>()
        val channelsIterator = factoriesChannels.iterator()
        while (channelsIterator.hasNext()) {
            val minionId = channelsIterator.next() as String
            @Suppress("UNCHECKED_CAST") val channels = channelsIterator.next() as List<String?>
            channels.forEachIndexed { index, channel ->
                if (channel != null) {
                    result.put(minionId, dagsAsList[index], channel)
                }
            }
        }
        return result
    }

    /**
     * Executes the script to assign the minions from the SHA key of the script.
     * If the SHA key is not valid, the script is first loaded into Redis before a new attempt.
     */
    private suspend fun executeFetchFactoryChannels(
        keyPrefixForUnassignedMinions: String,
        minionIds: Collection<MinionId>,
        dagsIds: Collection<DirectedAcyclicGraphName>
    ): List<*> {
        return try {
            redisScriptingCommands.evalsha(
                minionFetchFactoryChannelScriptSha, ScriptOutputType.MULTI,
                arrayOf(keyPrefixForUnassignedMinions),
                minionIds.joinToString(","),
                dagsIds.joinToString(",")
            )!!
        } catch (e: RedisNoScriptException) {
            minionFetchFactoryChannelScriptSha = redisScriptingCommands.scriptLoad(minionFetchFactoryChannelScript)!!
            log.debug { "Factory channel fetching script was loaded with SHA $minionFetchFactoryChannelScriptSha" }
            executeFetchFactoryChannels(keyPrefixForUnassignedMinions, minionIds, dagsIds)
        }
    }

    /**
     * Deletes all the remaining keys created for a campaign.
     */
    private suspend fun cleanCampaignKeys(campaignKey: CampaignKey) {
        val campaignKeysPattern = buildRedisKeyPrefix(campaignKey) + "*"
        val scanArgs = ScanArgs().match(campaignKeysPattern)
        var scanResult: KeyScanCursor<String>? = null
        do {
            scanResult = scanResult?.let { redisKeyCommands.scan(it, scanArgs) }
                ?: redisKeyCommands.scan(scanArgs)

            scanResult?.keys?.takeIf(List<String>::isNotEmpty)
                ?.let {
                    log.trace { "Deleting the campaign keys $it" }
                    redisKeyCommands.unlink(*it.toTypedArray())
                }
        } while (scanResult?.isFinished != true)
    }

    /**
     * Executes the script to complete the DAGS of a minion from the SHA key of the script.
     * If the SHA key is not valid, the script is first loaded into Redis before a new attempt.
     */
    private suspend fun executeCompletion(
        singletonsHashKey: String,
        countersHashKey: String,
        keyForAssignedDags: String,
        scenarioName: String,
        minionId: String,
        dagIdsCount: Int
    ): List<*> {
        return try {
            redisScriptingCommands.evalsha(
                minionCompletionScriptSha, ScriptOutputType.MULTI,
                arrayOf(singletonsHashKey, countersHashKey, keyForAssignedDags),
                scenarioName, minionId, dagIdsCount.toString()
            )!!
        } catch (e: RedisNoScriptException) {
            minionCompletionScriptSha = redisScriptingCommands.scriptLoad(minionCompletionScript)!!
            log.debug { "Completion script was loaded with SHA $minionCompletionScriptSha" }
            executeCompletion(
                singletonsHashKey,
                countersHashKey,
                keyForAssignedDags,
                scenarioName,
                minionId,
                dagIdsCount
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

        /**
         * Postfix of the Redis Set key containing all the minions IDs not yet fully assigned for a scenario.
         */
        const val UNASSIGNED_MINIONS = "minion:all-unassigned"

        /**
         * Postfix of the Redis Set key containing all the minions IDs under load for a scenario.
         */
        const val UNDERLOAD_MINIONS = "minion:under-load"

        /**
         * Prefix of the Redis Set key containing the not yet assigned DAGs of a minion.
         */
        const val MINION_UNASSIGNED_DAGS_PREFIX = "minion:unassigned-dags:"

        /**
         * Prefix of the Redis Hash key containing the assignments DAGs to distribution channels of a minion.
         */
        const val MINION_ASSIGNED_DAGS_PREFIX = "minion:assigned-dags:"

        /**
         * Postfix of the Redis Hash containing the counters for the scenarios and minions by scenarios.
         */
        const val CAMPAIGN_COUNTERS = "counters"

        /**
         * Postfix of the Redis Hash containing the counters for the scenarios singletons.
         */
        const val SINGLETON_REGISTRY = "singletons"

        /**
         * Name of the field of Redis Hash referenced by the prefix [CAMPAIGN_COUNTERS] that contains the count of
         * scenarios to execute in the campaign.
         *
         * The value is decremented each time a scenario is complete (all the minions in the scenario are complete).
         */
        const val SCENARIOS_COUNT_FIELD = "scenarios"

    }
}