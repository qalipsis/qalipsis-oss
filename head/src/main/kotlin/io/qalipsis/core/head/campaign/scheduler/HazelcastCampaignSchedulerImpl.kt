package io.qalipsis.core.head.campaign.scheduler

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.HazelcastInstanceAware
import com.hazelcast.nio.ObjectDataInput
import com.hazelcast.nio.ObjectDataOutput
import com.hazelcast.nio.serialization.DataSerializableFactory
import com.hazelcast.nio.serialization.IdentifiedDataSerializable
import com.hazelcast.scheduledexecutor.IScheduledExecutorService
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler
import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors.ORCHESTRATION_EXECUTOR_NAME
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignPreparator
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.security.TenantProvider
import io.qalipsis.core.head.security.UserProvider
import jakarta.annotation.PostConstruct
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Hazelcast-based implementation of [CampaignScheduler] that distributes scheduled campaign tasks
 * across the cluster using Hazelcast's [IScheduledExecutorService].
 *
 * When a campaign is scheduled, a [CampaignScheduleTask] is submitted to the Hazelcast scheduled executor,
 * which fires after the specified delay on one member of the cluster. The task publishes the campaign key to a
 * [com.hazelcast.topic.ITopic], and a listener on each head picks it up. To ensure single execution,
 * the handler entry in the distributed map is atomically removed — only the first member to remove it
 * proceeds with the campaign execution.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(bean = HazelcastInstance::class)
@Replaces(DefaultCampaignSchedulerImpl::class)
class HazelcastCampaignSchedulerImpl(
    private val hazelcastInstance: HazelcastInstance,
    userProvider: UserProvider,
    campaignExecutor: CampaignExecutor,
    tenantProvider: TenantProvider,
    campaignRepository: CampaignRepository,
    factoryService: FactoryService,
    campaignPreparator: CampaignPreparator,
    @Named(ORCHESTRATION_EXECUTOR_NAME) coroutineScope: CoroutineScope
) : AbstractCampaignScheduler(
    userProvider, campaignExecutor, tenantProvider, campaignRepository,
    factoryService, campaignPreparator, coroutineScope
) {

    @KTestable
    private lateinit var scheduler: IScheduledExecutorService

    @PostConstruct
    fun setup() {
        scheduler = hazelcastInstance.getScheduledExecutorService("default")
        hazelcastInstance.getReliableTopic<String>(CAMPAIGN_SCHEDULE_TOPIC)
            .addMessageListener { message ->
                val campaignKey = message.messageObject
                // Atomically remove the handler entry; only the first member to succeed processes the campaign.
                val handlerUrn =
                    hazelcastInstance.getMap<String, String>(SCHEDULE_HANDLERS_MAP).remove(campaignKey)
                if (handlerUrn != null) {
                    log.debug { "Processing scheduled campaign $campaignKey" }
                    coroutineScope.launch {
                        tryAndLog(log) {
                            executeCampaign(campaignKey)
                        }
                    }
                }
            }
    }

    override suspend fun schedule(campaignKey: CampaignKey, instant: Instant) {
        cancelSchedule(campaignKey)
        val delayMs = Duration.between(Instant.now(), instant).toMillis().coerceAtLeast(0)
        log.debug { "Scheduling campaign $campaignKey to execute in ${delayMs}ms" }
        val task = CampaignScheduleTask().apply { this.campaignKey = campaignKey }
        val future = scheduler.schedule<Unit>(task, delayMs, TimeUnit.MILLISECONDS)
        hazelcastInstance.getMap<String, String>(SCHEDULE_HANDLERS_MAP)[campaignKey] =
            future.handler.toUrn()
    }

    override suspend fun cancelSchedule(campaignKey: CampaignKey) {
        hazelcastInstance.getMap<String, String>(SCHEDULE_HANDLERS_MAP).remove(campaignKey)
            ?.let { urn ->
                log.debug { "Cancelling scheduled task for campaign $campaignKey" }
                tryAndLogOrNull(log) {
                    scheduler.getScheduledFuture<Unit>(ScheduledTaskHandler.of(urn)).dispose()
                }
            }
    }

    companion object {

        private const val CAMPAIGN_SCHEDULE_TOPIC = "campaign-schedules"

        private const val SCHEDULE_HANDLERS_MAP = "campaign-schedule-handlers"

        private val log = logger()
    }

    /**
     * Hazelcast task that publishes the campaign key to a [com.hazelcast.topic.ITopic] when the scheduled
     * delay elapses. The task is serialized and distributed across the Hazelcast cluster.
     */
    class CampaignScheduleTask : Runnable, IdentifiedDataSerializable, HazelcastInstanceAware {

        lateinit var campaignKey: String

        private lateinit var hazelcastInstance: HazelcastInstance

        override fun run() {
            hazelcastInstance.getReliableTopic<String>(CAMPAIGN_SCHEDULE_TOPIC)
                .publish(campaignKey)
        }

        override fun writeData(output: ObjectDataOutput) {
            output.writeString(campaignKey)
        }

        override fun readData(input: ObjectDataInput) {
            campaignKey = input.readString()!!
        }

        override fun getFactoryId(): Int = CampaignScheduleTaskFactory.FACTORY_ID

        override fun getClassId(): Int = CampaignScheduleTaskFactory.TASK_ID

        override fun setHazelcastInstance(hazelcastInstance: HazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance
        }
    }

    /**
     * Factory for deserialization of [CampaignScheduleTask] instances by Hazelcast.
     */
    class CampaignScheduleTaskFactory : DataSerializableFactory {

        override fun create(typeId: Int): IdentifiedDataSerializable? {
            return if (typeId == TASK_ID) {
                CampaignScheduleTask()
            } else {
                null
            }
        }

        companion object {

            const val TASK_ID = 1

            const val FACTORY_ID = 2
        }
    }
}
