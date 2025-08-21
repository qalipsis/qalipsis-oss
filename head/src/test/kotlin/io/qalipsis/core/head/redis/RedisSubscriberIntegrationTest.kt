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

package io.qalipsis.core.head.redis

import io.aerisconsulting.catadioptre.setProperty
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.configuration.ExecutionConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.feedbacks.CampaignShutdownFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.communication.FeedbackListener
import io.qalipsis.core.head.communication.HandshakeRequestListener
import io.qalipsis.core.head.communication.HeartbeatListener
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import jakarta.inject.Named
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExperimentalLettuceCoroutinesApi
@WithMockk
@PropertySource(
    Property(name = ExecutionConfiguration.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = "redis"),
    Property(name = "head.handshake-request-channel", value = RedisSubscriberIntegrationTest.HANDSHAKE_REQUEST_CHANNEL),
    Property(name = "head.heartbeat-channel", value = RedisSubscriberIntegrationTest.HEARTBEAT_CHANNEL)
)
@MicronautTest(
    environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD],
    startApplication = false
)
internal class RedisSubscriberIntegrationTest : AbstractRedisIntegrationTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK(name = "feedbackListener1")
    private lateinit var feedbackListener1: FeedbackListener<Feedback>

    @RelaxedMockK(name = "feedbackListener2")
    private lateinit var feedbackListener2: FeedbackListener<Feedback>

    @RelaxedMockK(name = "feedbackListener3")
    private lateinit var feedbackListener3: FeedbackListener<Feedback>

    @RelaxedMockK(name = "handshakeRequestListener1")
    private lateinit var handshakeRequestListener1: HandshakeRequestListener

    @RelaxedMockK(name = "handshakeRequestListener2")
    private lateinit var handshakeRequestListener2: HandshakeRequestListener

    @RelaxedMockK(name = "heartbeatRequestListener1")
    private lateinit var heartbeatRequestListener1: HeartbeatListener

    @RelaxedMockK(name = "heartbeatRequestListener2")
    private lateinit var heartbeatRequestListener2: HeartbeatListener

    @Inject
    private lateinit var redisHeadChannel: RedisHeadChannel

    @Inject
    private lateinit var redisSubscriber: RedisSubscriber

    @Inject
    @field:Named(RedisPubSubConfiguration.PUBLISHER_BEAN_NAME)
    private lateinit var publisherCommands: RedisPubSubReactiveCommands<String, ByteArray>

    @Inject
    @field:Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME)
    private lateinit var subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>

    @Inject
    private lateinit var serializer: DistributionSerializer

    private var initialized = false

    @MockBean(CampaignService::class)
    fun campaignService() = relaxedMockk<CampaignService>()

    @BeforeEach
    internal fun setUp() {
        if (!initialized) {
            initialized = true

            redisSubscriber.setProperty(
                "feedbackListeners",
                listOf(feedbackListener1, feedbackListener2, feedbackListener3)
            )
            redisSubscriber.setProperty(
                "handshakeRequestListeners",
                listOf(handshakeRequestListener1, handshakeRequestListener2)
            )
            redisSubscriber.setProperty(
                "heartbeatListeners",
                listOf(heartbeatRequestListener1, heartbeatRequestListener2)
            )
        }
        redisSubscriber.init()
    }

    @AfterEach
    internal fun tearDown() {
        redisSubscriber.subscribedHandshakeRequestsChannels.clear()
        redisSubscriber.subscribedFeedbackChannels.clear()
        redisSubscriber.close()
        subscriberCommands.quit().toFuture().get()
        connection.sync().flushdb()
    }

    @Test
    @Timeout(5)
    internal fun `should receive the feedback`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(2)
        every { feedbackListener1.accept(any()) } returns true
        every { feedbackListener2.accept(any()) } returns false
        every { feedbackListener3.accept(any()) } returns true
        coEvery { feedbackListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { feedbackListener3.notify(any()) } coAnswers { countLatch.decrement() }

        // when
        redisHeadChannel.subscribeFeedback(FEEDBACK_CHANNEL)

        // when
        val feedback = CampaignShutdownFeedback("the-campaign", FeedbackStatus.COMPLETED)
        publisherCommands.publish(FEEDBACK_CHANNEL, serializer.serialize(feedback)).subscribe()
        countLatch.await()

        // then
        coVerifyOnce {
            feedbackListener1.accept(eq(feedback))
            feedbackListener2.accept(eq(feedback))
            feedbackListener3.accept(eq(feedback))
            feedbackListener1.notify(eq(feedback))
            feedbackListener3.notify(eq(feedback))
        }
        confirmVerified(
            feedbackListener1,
            feedbackListener2,
            feedbackListener3,
            handshakeRequestListener1,
            handshakeRequestListener2,
            heartbeatRequestListener1,
            heartbeatRequestListener2
        )
    }

    @Test
    @Timeout(5)
    internal fun `should receive the handshake request`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(2)
        coEvery { handshakeRequestListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { handshakeRequestListener2.notify(any()) } coAnswers { countLatch.decrement() }

        // when
        val handshakeRequest = HandshakeRequest(
            nodeId = "the-node-id",
            tags = mapOf("key-1" to "value-1", "key-2" to "value-2"),
            replyTo = "the-response",
            scenarios = emptyList(),
            zone = "fr"
        )
        publisherCommands.publish(HANDSHAKE_REQUEST_CHANNEL, serializer.serialize(handshakeRequest)).subscribe()
        countLatch.await()

        // then
        coVerifyOnce {
            handshakeRequestListener1.notify(eq(handshakeRequest))
            handshakeRequestListener2.notify(eq(handshakeRequest))
        }
        confirmVerified(
            feedbackListener1,
            feedbackListener2,
            feedbackListener3,
            handshakeRequestListener1,
            handshakeRequestListener2,
            heartbeatRequestListener1,
            heartbeatRequestListener2
        )
    }

    @Test
    @Timeout(5)
    internal fun `should receive the heartbeat`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(2)
        coEvery { heartbeatRequestListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { heartbeatRequestListener2.notify(any()) } coAnswers { countLatch.decrement() }

        // when
        val heartbeat = Heartbeat(
            nodeId = "the-node-id",
            tenant = "the-tenant",
            timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        )
        publisherCommands.publish(HEARTBEAT_CHANNEL, serializer.serialize(heartbeat)).subscribe()
        countLatch.await()

        // then
        coVerifyOnce {
            heartbeatRequestListener1.notify(eq(heartbeat))
            heartbeatRequestListener2.notify(eq(heartbeat))
        }
        confirmVerified(
            feedbackListener1,
            feedbackListener2,
            feedbackListener3,
            handshakeRequestListener1,
            handshakeRequestListener2,
            heartbeatRequestListener1,
            heartbeatRequestListener2
        )
    }

    companion object {

        const val FEEDBACK_CHANNEL = "the-feedback-channel"

        const val HANDSHAKE_REQUEST_CHANNEL = "the-handshake-request-channel"

        const val HEARTBEAT_CHANNEL = "the-heartbeat-request-channel"
    }
}