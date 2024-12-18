/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.campaign.states

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.HazelcastInstanceAware
import com.hazelcast.nio.ObjectDataInput
import com.hazelcast.nio.ObjectDataOutput
import com.hazelcast.nio.serialization.DataSerializableFactory
import com.hazelcast.nio.serialization.IdentifiedDataSerializable
import com.hazelcast.scheduledexecutor.IScheduledExecutorService
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.serialization.DistributionSerializer
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Implementation of [DelayedFeedbackManager] that relies on Hazelcast to distribute the tasks and ensure
 * that the feedbacks are for sure propagated.
 */
@Singleton
@Requires(bean = HazelcastInstance::class)
internal class DistributedDelayedFeedbackManager(
    private val hazelcastInstance: HazelcastInstance,
    private val headChannel: HeadChannel,
    private val configuration: HeadConfiguration,
    private val serializer: DistributionSerializer
) : DelayedFeedbackManager {

    /**
     * ID of the listener as provided by Hazelcast.
     */
    private var listenerId: UUID? = null

    private lateinit var scheduler: IScheduledExecutorService

    @PostConstruct
    fun init() {
        scheduler = hazelcastInstance.getScheduledExecutorService("default")
        listenerId = hazelcastInstance.getReliableTopic<DelayedFeedbackTask>(DELAYED_FEEDBACK_TOPIC)
            .addMessageListener { message ->
                val delayedFeedback = message.messageObject
                log.debug { "Publishing a delayed feedback to channel ${delayedFeedback.channelName} for the campaign ${delayedFeedback.campaignKey}" }
                runBlocking {
                    headChannel.publishFeedback(
                        channelName = delayedFeedback.channelName,
                        campaignKey = delayedFeedback.campaignKey,
                        serializedFeedback = delayedFeedback.serializedFeedback
                    )
                }
            }
    }

    @LogInput
    override fun scheduleCancellation(channelName: DispatcherChannel, feedback: Feedback) {
        val task = DelayedFeedbackTask()
        task.channelName = channelName
        task.campaignKey = (feedback as CampaignManagementFeedback).campaignKey
        task.serializedFeedback = serializer.serialize(feedback)

        scheduler.schedule<Unit>(task, configuration.campaignCancellationStateGracePeriod.toSeconds(), TimeUnit.SECONDS)
    }

    companion object {

        private const val DELAYED_FEEDBACK_TOPIC = "delayed-feedbacks"

        private val log = logger()
    }

    /**
     * Tasks that generates a feedback to a Hazelcast topic when executed.
     */
    internal class DelayedFeedbackTask : Runnable, IdentifiedDataSerializable,
        HazelcastInstanceAware {

        lateinit var serializedFeedback: ByteArray

        lateinit var channelName: DispatcherChannel

        lateinit var campaignKey: String

        private lateinit var hazelcastInstance: HazelcastInstance

        override fun run() {
            // Just publishes itself into a topic.
            hazelcastInstance.getReliableTopic<DelayedFeedbackTask>(DELAYED_FEEDBACK_TOPIC).publish(this)
        }

        override fun writeData(output: ObjectDataOutput) {
            output.writeString(channelName)
            output.writeString(campaignKey)
            output.writeByteArray(serializedFeedback)
        }

        override fun readData(input: ObjectDataInput) {
            channelName = input.readString()!!
            campaignKey = input.readString()!!
            serializedFeedback = input.readByteArray()!!
        }

        override fun getFactoryId(): Int = DelayedFeedbackTaskFactory.FACTORY_ID

        override fun getClassId(): Int = DelayedFeedbackTaskFactory.FEEDBACK_TASK_ID

        override fun setHazelcastInstance(hazelcastInstance: HazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance
        }

    }


    /**
     * Factory to manage the deserialization of [DelayedFeedbackTask]s.
     */
    internal class DelayedFeedbackTaskFactory : DataSerializableFactory {

        override fun create(typeId: Int): IdentifiedDataSerializable? {
            return if (typeId == FEEDBACK_TASK_ID) {
                DelayedFeedbackTask()
            } else {
                null
            }
        }

        companion object {

            const val FEEDBACK_TASK_ID = 1

            const val FACTORY_ID = 1
        }
    }
}