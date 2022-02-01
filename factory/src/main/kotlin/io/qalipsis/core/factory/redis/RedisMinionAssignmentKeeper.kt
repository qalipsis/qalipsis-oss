package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.ValueScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInputAndOutput
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
@Requires(beans = [StatefulRedisConnection::class])
internal class RedisMinionAssignmentKeeper(
    private val factoryConfiguration: FactoryConfiguration,
    private val redisCommands: RedisCoroutinesCommands<String, String>,
    private val localAssignmentStore: LocalAssignmentStore
) : MinionAssignmentKeeper {

    val keysPrefix = RedisUtils.buildKeysPrefixForTenant(factoryConfiguration.tenant)

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

    @PostConstruct
    fun init() {
        minionAssignmentScript = RedisUtils.loadScript("/redis/minion-assignment.lua")
        minionCompletionScript = RedisUtils.loadScript("/redis/minion-dag-completion.lua")
        minionRegistrationScript = RedisUtils.loadScript("/redis/register-minions.lua")
    }

    @LogInputAndOutput
    override suspend fun assignFactoryDags(
        campaignId: CampaignId,
        dagsByScenarios: Map<ScenarioId, Collection<DirectedAcyclicGraphId>>
    ) = assignFactoryDags(campaignId, dagsByScenarios, factoryConfiguration.nodeId)

    /**
     * Factory-configurable version of [assignFactoryDags] for testing purpose.
     */
    private suspend fun assignFactoryDags(
        campaignId: CampaignId,
        dagsByScenarios: Map<ScenarioId, Collection<DirectedAcyclicGraphId>>,
        factoryNodeId: String
    ) {
        dagsByScenarios.forEach { (scenarioId, dagIds) ->
            val keyForFactoryAssignment = buildRedisKeyPrefix(campaignId, scenarioId) + factoryNodeId
            redisCommands.sadd(keyForFactoryAssignment, *dagIds.toTypedArray())
        }
    }

    /**
     * Creates the common prefix of all the keys used in the assignment process.
     */
    private fun buildRedisKeyPrefix(
        campaignId: CampaignId,
        scenarioId: ScenarioId
    ) = buildRedisKeyPrefix(campaignId) + "$scenarioId:"

    /**
     * Creates the common prefix of all the keys used in the assignment process.
     *
     * A Hash tag is used in the key prefix to locate all the values related to the same campaign.
     */
    private fun buildRedisKeyPrefix(
        campaignId: CampaignId
    ) = "${keysPrefix}{$campaignId}-assignment:"

    @LogInputAndOutput
    override suspend fun registerMinionsToAssign(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        dagIds: Collection<DirectedAcyclicGraphId>,
        minionIds: Collection<MinionId>,
        underLoad: Boolean
    ) {
        val campaignRelatedKeyPrefix = buildRedisKeyPrefix(campaignId)
        val scenarioRelatedKeyPrefix = buildRedisKeyPrefix(campaignId, scenarioId)

        // Create the set with the minions to register.
        val minionsSet = scenarioRelatedKeyPrefix + Math.random()
        redisCommands.sadd(minionsSet, *minionIds.toTypedArray())

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
            scenarioId,
            dagIds,
            underLoad
        )
    }

    /**
     * Executes the script to complete the DAGS of a minion from the SHA key of the script.
     * If the SHA key is not valid, the script is first loaded into Redis before a new attempt.
     */
    private suspend fun executeRegistration(
        minionsSetKey: String,
        unassignedMinionsSetKey: String,
        underLoadMinionsSetKey: String,
        keyPrefixForUnassignedDags: String,
        countersHashKey: String,
        singletonsHashKey: String,
        scenarioId: String,
        dagIds: Collection<String>,
        underLoad: Boolean
    ): Boolean {
        return try {
            redisCommands.evalsha(
                minionRegistrationScriptSha, ScriptOutputType.BOOLEAN,
                arrayOf(
                    minionsSetKey,
                    unassignedMinionsSetKey,
                    underLoadMinionsSetKey,
                    keyPrefixForUnassignedDags,
                    countersHashKey,
                    singletonsHashKey
                ),
                scenarioId, dagIds.joinToString(separator = ","), underLoad.toString()
            )!!
        } catch (e: RedisNoScriptException) {
            minionRegistrationScriptSha = redisCommands.scriptLoad(minionRegistrationScript)!!
            log.debug { "Registration script was loaded with SHA $minionRegistrationScriptSha" }
            executeRegistration(
                minionsSetKey,
                unassignedMinionsSetKey,
                underLoadMinionsSetKey,
                keyPrefixForUnassignedDags,
                countersHashKey,
                singletonsHashKey,
                scenarioId,
                dagIds,
                underLoad
            )
        }
    }

    override suspend fun getIdsOfMinionsUnderLoad(
        campaignId: CampaignId,
        scenarioId: ScenarioId
    ): Collection<MinionId> {
        val unassignedMinionsSetKey = buildRedisKeyPrefix(campaignId, scenarioId) + UNDERLOAD_MINIONS
        val minionsIds = mutableListOf<MinionId>()
        var scanResult: ValueScanCursor<String>? = null
        do {
            scanResult = scanResult?.let { redisCommands.sscan(unassignedMinionsSetKey, it) }
                ?: redisCommands.sscan(unassignedMinionsSetKey)

            scanResult?.let { minionsIds += it.values }
        } while (scanResult?.isFinished != true)
        return minionsIds
    }

    override suspend fun completeUnassignedMinionsRegistration(campaignId: CampaignId, scenarioId: ScenarioId) {
        // Updates the number active scenarios in the campaign.
        val countersHashKey = buildRedisKeyPrefix(campaignId) + CAMPAIGN_COUNTERS
        redisCommands.hincrby(countersHashKey, SCENARIOS_COUNT_FIELD, 1)
    }

    @LogInputAndOutput
    override suspend fun assign(
        campaignId: CampaignId,
        scenarioId: ScenarioId
    ) = assign(campaignId, scenarioId, factoryConfiguration.nodeId, factoryConfiguration.distributionChannel)

    /**
     * Factory-configurable version of [assign] for testing purpose.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun assign(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        factoryNodeId: String,
        factoryChannelName: String
    ): Map<MinionId, Collection<DirectedAcyclicGraphId>> {
        val allKeysPrefix = buildRedisKeyPrefix(campaignId, scenarioId)
        val keyForFactoryAssignment = allKeysPrefix + factoryNodeId
        val keyPrefixForUnassignedMinions = allKeysPrefix + UNASSIGNED_MINIONS
        val keyPrefixForUnassignedDags = allKeysPrefix + MINION_UNASSIGNED_DAGS_PREFIX
        val keyPrefixForAssignedDags = allKeysPrefix + MINION_ASSIGNED_DAGS_PREFIX

        var evaluatedMinionsCount: Long

        val assignments = mutableMapOf<String, List<String>>()
        try {
            withTimeout(factoryConfiguration.assignment.timeout.toMillis()) {
                val evaluationBatchSize = "${factoryConfiguration.assignment.evaluationBatchSize}"
                do {
                    val assignedMinions = executeAssignment(
                        keyPrefixForUnassignedMinions, keyForFactoryAssignment,
                        factoryChannelName, evaluationBatchSize, keyPrefixForUnassignedDags, keyPrefixForAssignedDags
                    )
                    // Number of minions that were evaluated for assignment. When it is 0, it means that all the minions
                    // were assigned.
                    evaluatedMinionsCount = assignedMinions[1] as Long

                    // Actual assignments to add to the local factory, identifier by a succession of minion IDs / list of DAGs.
                    assignments += (assignedMinions[3] as List<*>).windowed(2, 2).map {
                        val minionId = it[0] as String
                        val dags = it[1] as List<String>
                        minionId to dags
                    }
                } while (evaluatedMinionsCount > 0)
            }
        } finally {
            redisCommands.unlink(keyForFactoryAssignment)
        }

        localAssignmentStore.save(scenarioId, assignments)
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
        keyPrefixForAssignedDags: String
    ): List<*> {
        return try {
            redisCommands.evalsha(
                minionAssignmentScriptSha, ScriptOutputType.MULTI,
                arrayOf(keyPrefixForUnassignedMinions, keyForFactoryAssignment),

                factoryChannelName,
                evaluationBatchSize,
                keyPrefixForUnassignedDags,
                keyPrefixForAssignedDags
            )!!
        } catch (e: RedisNoScriptException) {
            minionAssignmentScriptSha = redisCommands.scriptLoad(minionAssignmentScript)!!
            log.debug { "Assignment script was loaded with SHA $minionAssignmentScriptSha" }
            executeAssignment(
                keyPrefixForUnassignedMinions,
                keyForFactoryAssignment,
                factoryChannelName,
                evaluationBatchSize,
                keyPrefixForUnassignedDags,
                keyPrefixForAssignedDags
            )
        }
    }

    @LogInputAndOutput
    override suspend fun executionComplete(
        campaignId: CampaignId,
        scenarioId: ScenarioId,
        minionId: MinionId,
        dagIds: Collection<DirectedAcyclicGraphId>
    ): CampaignCompletionState {
        val singletonsHashKey = buildRedisKeyPrefix(campaignId) + SINGLETON_REGISTRY
        val countersHashKey = buildRedisKeyPrefix(campaignId) + CAMPAIGN_COUNTERS
        val keyForAssignedDags = buildRedisKeyPrefix(campaignId, scenarioId) + MINION_ASSIGNED_DAGS_PREFIX + minionId
        val completionsFlags =
            executeCompletion(singletonsHashKey, countersHashKey, keyForAssignedDags, scenarioId, minionId, dagIds.size)
        val state = CampaignCompletionState()
        state.minionComplete = (completionsFlags[1] as Number).toInt() > 0
        state.scenarioComplete = (completionsFlags[3] as Number).toInt() > 0
        state.campaignComplete = (completionsFlags[5] as Number).toInt() > 0
        if (state.campaignComplete) {
            cleanCampaignKeys(campaignId)
        }
        return state
    }

    /**
     * Deletes all the remaining keys created for a campaign.
     */
    private suspend fun cleanCampaignKeys(campaignId: CampaignId) {
        val campaignKeysPattern = buildRedisKeyPrefix(campaignId) + "*"
        val scanArgs = ScanArgs().match(campaignKeysPattern)
        var scanResult: KeyScanCursor<String>? = null
        do {
            scanResult = scanResult?.let { redisCommands.scan(it, scanArgs) }
                ?: redisCommands.scan(scanArgs)

            scanResult?.keys?.takeIf(List<String>::isNotEmpty)
                ?.let {
                    log.trace { "Deleting the campaign keys $it" }
                    redisCommands.unlink(*it.toTypedArray())
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
        scenarioId: String,
        minionId: String,
        dagIdsCount: Int
    ): List<*> {
        return try {
            redisCommands.evalsha(
                minionCompletionScriptSha, ScriptOutputType.MULTI,
                arrayOf(singletonsHashKey, countersHashKey, keyForAssignedDags),
                scenarioId, minionId, dagIdsCount.toString()
            )!!
        } catch (e: RedisNoScriptException) {
            minionCompletionScriptSha = redisCommands.scriptLoad(minionCompletionScript)!!
            log.debug { "Completion script was loaded with SHA $minionCompletionScriptSha" }
            executeCompletion(singletonsHashKey, countersHashKey, keyForAssignedDags, scenarioId, minionId, dagIdsCount)
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