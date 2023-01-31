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
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
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
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.CommunicationChannelConfiguration
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.redis.RedisUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * [MinionAssignmentKeeper] for cluster-distributed minions, where some minions might split their own execution workflow
 * across several factories.
 *
 * @author Eric Jessé
 */
@ExperimentalLettuceCoroutinesApi
@Singleton
@Requirements(
    Requires(beans = [StatefulRedisConnection::class]),
    Requires(env = [ExecutionEnvironments.FACTORY]),
    Requires(notEnv = [ExecutionEnvironments.SINGLE_FACTORY]),
    Requires(property = "factory.assignment.strategy", value = "distributed-minion")
)
internal class RedisDistributedMinionAssignmentKeeper(
    private val factoryConfiguration: FactoryConfiguration,
    private val scenarioRegistry: ScenarioRegistry,
    communicationChannelConfiguration: CommunicationChannelConfiguration,
    private val redisScriptingCommands: RedisScriptingCoroutinesCommands<String, String>,
    redisSetCommands: RedisSetCoroutinesCommands<String, String>,
    redisHashCommands: RedisHashCoroutinesCommands<String, String>,
    private val redisKeyCommands: RedisKeyCoroutinesCommands<String, String>,
    private val localAssignmentStore: LocalAssignmentStore
) : RedisSingleLocationMinionAssignmentKeeper(
    factoryConfiguration,
    scenarioRegistry,
    communicationChannelConfiguration,
    redisScriptingCommands,
    redisSetCommands,
    redisHashCommands,
    redisKeyCommands,
    localAssignmentStore
) {

    /**
     * Script to assign the minions.
     */
    private lateinit var minionAssignmentScript: ByteArray

    /**
     * Script to fetch the factories channels to forward the execution contexts.
     */
    private lateinit var minionFetchFactoryChannelScript: ByteArray

    /**
     * SHA key of the script to assign the minions.
     */
    private var minionAssignmentScriptSha: String = "befc3c70eec49f29c5f9a9f50e617fda5945d7d6"

    /**
     * SHA key of the script to fetch the factories channels to forward the execution contexts.
     */
    private var minionFetchFactoryChannelScriptSha: String = "1842437f4e1b3a2942b3fcdc1100f44a86b29c83"

    /**
     * Mutex to avoid concurrent loading of scripts into Redis.
     */
    private val scriptLoaderMutex = Mutex()

    init {
        log.debug { "Using the RedisDistributedMinionAssignmentKeeper to assign the minions" }
    }

    @PostConstruct
    override fun init() {
        super.init()
        minionAssignmentScript = RedisUtils.loadScript("/redis/minion-assignment-for-distributed-minion.lua")
        minionFetchFactoryChannelScript = RedisUtils.loadScript("/redis/minion-get-factories-channels.lua")
    }

    /**
     * Factory-configurable version of [assign] for testing purpose.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun assign(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        factoryNodeId: String,
        factoryChannelName: String,
        maximalMinionsCount: Int
    ): Map<MinionId, Collection<DirectedAcyclicGraphName>> {
        val allKeysPrefix = buildRedisKeyPrefix(campaignKey, scenarioName)
        val rootDag = scenarioRegistry[scenarioName]!!.dags.first { it.isUnderLoad && it.isRoot }.name
        val keyForFactoryAssignment = allKeysPrefix + factoryNodeId
        val keyPrefixForUnassignedMinions = allKeysPrefix + UNASSIGNED_MINIONS
        val keyPrefixForUnassignedDags = allKeysPrefix + MINION_UNASSIGNED_DAGS_PREFIX
        val keyPrefixForAssignedDags = allKeysPrefix + MINION_ASSIGNED_DAGS_PREFIX
        val keyForRootFactoryOwnersHash = allKeysPrefix + MINION_ROOT_FACTORY_CHANNEL_NAME

        var evaluatedMinionsCount: Long
        val minionsUnderLoad = getIdsOfMinionsUnderLoad(campaignKey, scenarioName).toSet()

        val assignments = mutableMapOf<String, List<String>>()
        var assignedUnderLoad = 0
        try {
            withTimeout(factoryConfiguration.assignment.timeout.toMillis()) {
                val evaluationBatchSize = "${factoryConfiguration.assignment.evaluationBatchSize}"
                do {
                    val assignedMinions = executeAssignment(
                        keyPrefixForUnassignedMinions = keyPrefixForUnassignedMinions,
                        keyForFactoryAssignment = keyForFactoryAssignment,
                        minionRootDagMinionKey = keyForRootFactoryOwnersHash,
                        factoryChannelName = factoryChannelName,
                        rootDag = rootDag,
                        evaluationBatchSize = evaluationBatchSize,
                        keyPrefixForUnassignedDags = keyPrefixForUnassignedDags,
                        keyPrefixForAssignedDags = keyPrefixForAssignedDags,
                        maximalMinionsCount = "${maximalMinionsCount - assignedUnderLoad}"
                    )
                    // Number of minions that were evaluated for assignment. When it is 0, it means that all the minions
                    // were assigned.
                    evaluatedMinionsCount = assignedMinions[1] as Long

                    // Actual assignments to add to the local factory, identifier by a succession of minion IDs / list of DAGs.
                    val newlyAssignedMinions = (assignedMinions[3] as List<*>).windowed(2, 2).associate {
                        val minionId = it[0] as String
                        val dags = it[1] as List<String>
                        minionId to dags
                    }

                    // Counts the assigned under load to respect the limit, the singletons being ignored here.
                    assignedUnderLoad += newlyAssignedMinions.keys.intersect(minionsUnderLoad).size

                    assignments += newlyAssignedMinions
                } while (evaluatedMinionsCount > 0 && assignedUnderLoad < maximalMinionsCount)
            }
        } finally {
            redisKeyCommands.unlink(keyForFactoryAssignment)
        }

        if (log.isTraceEnabled) {
            log.trace { "Assignment of factory $factoryNodeId for campaign $campaignKey and scenario $scenarioName (${assignments.size} minions assigned): $assignments" }
        } else if (log.isDebugEnabled) {
            log.debug { "${assignments.size} minions assigned to factory $factoryNodeId for campaign $campaignKey and scenario $scenarioName" }
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
        minionRootDagMinionKey: String,
        factoryChannelName: String,
        rootDag: String,
        evaluationBatchSize: String,
        keyPrefixForUnassignedDags: String,
        keyPrefixForAssignedDags: String,
        maximalMinionsCount: String
    ): List<*> {
        return try {
            redisScriptingCommands.evalsha(
                minionAssignmentScriptSha, ScriptOutputType.MULTI,
                arrayOf(keyPrefixForUnassignedMinions, keyForFactoryAssignment, minionRootDagMinionKey),

                factoryChannelName,
                rootDag,
                evaluationBatchSize,
                keyPrefixForUnassignedDags,
                keyPrefixForAssignedDags,
                maximalMinionsCount
            )!!
        } catch (e: RedisNoScriptException) {
            minionAssignmentScriptSha =
                scriptLoaderMutex.withLock { redisScriptingCommands.scriptLoad(minionAssignmentScript)!! }
            log.debug { "Assignment script was loaded with SHA $minionAssignmentScriptSha" }
            executeAssignment(
                keyPrefixForUnassignedMinions,
                keyForFactoryAssignment,
                minionRootDagMinionKey,
                factoryChannelName,
                rootDag,
                evaluationBatchSize,
                keyPrefixForUnassignedDags,
                keyPrefixForAssignedDags,
                maximalMinionsCount
            )
        }
    }

    override suspend fun getFactoriesChannels(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        minionIds: Collection<MinionId>,
        dagsIds: Collection<DirectedAcyclicGraphName>
    ): Table<MinionId, DirectedAcyclicGraphName, String> {
        val keyPrefixForAssignedDags = buildRedisKeyPrefix(
            campaignKey,
            scenarioName
        ) + RedisSingleLocationMinionAssignmentKeeper.MINION_ASSIGNED_DAGS_PREFIX
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
            minionFetchFactoryChannelScriptSha =
                scriptLoaderMutex.withLock { redisScriptingCommands.scriptLoad(minionFetchFactoryChannelScript)!! }
            log.debug { "Factory channel fetching script was loaded with SHA $minionFetchFactoryChannelScriptSha" }
            executeFetchFactoryChannels(keyPrefixForUnassignedMinions, minionIds, dagsIds)
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
         * Prefix of the Redis Set key containing the not yet assigned DAGs of a minion.
         */
        const val MINION_UNASSIGNED_DAGS_PREFIX = "minion:unassigned-dags:"

        /**
         * Prefix of the Redis Hash key containing the assignments DAGs to distribution channels of a minion.
         */
        const val MINION_ASSIGNED_DAGS_PREFIX = "minion:assigned-dags:"

    }
}