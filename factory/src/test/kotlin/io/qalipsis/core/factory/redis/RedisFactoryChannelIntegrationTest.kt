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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.api.reactive.ChannelMessage
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.every
import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.feedbacks.EndOfCampaignFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.RegistrationScenario
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.runBlocking
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
    Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS),
    Property(name = "factory.handshake.request-channel", value = RedisFactoryChannelIntegrationTest.HANDSHAKE_CHANNEL),
    Property(name = "factory.node-id", value = "my-node"),
    Property(name = "factory.tenant", value = "my-tenant")
)
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.FACTORY], startApplication = false)
internal class RedisFactoryChannelIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var redisFactoryChannel: RedisFactoryChannel

    @Inject
    @field:Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME)
    private lateinit var subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>

    @Inject
    private lateinit var subscriber: RedisSubscriber

    private lateinit var captured: Channel<ChannelMessage<String, ByteArray>>

    private val listener = object : RedisPubSubListener<String, ByteArray> {
        override fun message(channel: String, message: ByteArray) {
            captured.trySendBlocking(ChannelMessage(channel, message))
        }

        override fun message(pattern: String, channel: String, message: ByteArray) {
            message(channel, message)
        }

        override fun subscribed(channel: String, count: Long) = Unit
        override fun psubscribed(pattern: String, count: Long) = Unit
        override fun unsubscribed(channel: String, count: Long) = Unit
        override fun punsubscribed(pattern: String, count: Long) = Unit
    }

    @BeforeEach
    internal fun setUp() {
        captured = Channel(1)
        subscriberCommands.statefulConnection.addListener(listener)
        subscriberCommands.psubscribe("*").toFuture().get()
    }

    @AfterEach
    internal fun tearDown() {
        subscriberCommands.quit().toFuture().get()
        subscriberCommands.statefulConnection.removeListener(listener)
        captured.cancel()
    }

    @Test
    @Timeout(5)
    internal fun `should send the directive with specific target`() = testDispatcherProvider.run {
        // when
        redisFactoryChannel.publishDirective(TestDescriptiveDirective(1, FACTORY_CHANNEL))

        // then
        val received = captured.receive()
        assertThat(received).all {
            prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(FACTORY_CHANNEL)
            transform("message") { subscriber.subscriberRegistry.serializer.deserialize<Directive>(it.message) }.isNotNull()
                .isInstanceOf(TestDescriptiveDirective::class).prop(TestDescriptiveDirective::value).isEqualTo(1)
        }
    }

    @Test
    @Timeout(5)
    internal fun `should send the referenced directive with specific target`() = testDispatcherProvider.run {
        // when
        redisFactoryChannel.publishDirective(
            MinionsDeclarationDirective(
                "the campaign",
                "the scenario",
                1000,
                FACTORY_CHANNEL
            )
        )

        // then
        val received = captured.receive()
        assertThat(received).all {
            prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(FACTORY_CHANNEL)
            transform("message") { subscriber.subscriberRegistry.serializer.deserialize<Directive>(it.message) }.isNotNull()
                .isInstanceOf(MinionsDeclarationDirectiveReference::class)
                // Dereferences the received directive.
                .transform { runBlocking { subscriber.subscriberRegistry.directiveRegistry.get(it) } }.isNotNull()
                .isInstanceOf(MinionsDeclarationDirective::class).all {
                    prop(MinionsDeclarationDirective::campaignKey).isEqualTo("the campaign")
                    prop(MinionsDeclarationDirective::scenarioName).isEqualTo("the scenario")
                    prop(MinionsDeclarationDirective::minionsCount).isEqualTo(1000)
                }
        }
    }

    @Test
    internal fun `should send the broadcasted directive when no channel is set`() = testDispatcherProvider.run {
        // given
        redisFactoryChannel.init(relaxedMockk {
            every { broadcastChannel } returns BROADCAST_CHANNEL
        })

        // when
        redisFactoryChannel.publishDirective(TestDescriptiveDirective(2, "  "))

        // then
        val received = captured.receive()
        assertThat(received).all {
            prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(BROADCAST_CHANNEL)
            transform("message") { subscriber.subscriberRegistry.serializer.deserialize<Directive>(it.message) }.isNotNull()
                .isInstanceOf(TestDescriptiveDirective::class).prop(TestDescriptiveDirective::value).isEqualTo(2)
        }
    }

    @Test
    internal fun `should send the directive to the channel specified as argument, not in directive`() =
        testDispatcherProvider.run {
            // given
            redisFactoryChannel.init(relaxedMockk {
                every { broadcastChannel } returns BROADCAST_CHANNEL
            })

            // when
            redisFactoryChannel.publishDirective(FACTORY_CHANNEL, TestDescriptiveDirective(3, "other-channel"))

            // then
            val received = captured.receive()
            assertThat(received).all {
                prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(FACTORY_CHANNEL)
                transform("message") { subscriber.subscriberRegistry.serializer.deserialize<Directive>(it.message) }.isNotNull()
                    .isInstanceOf(TestDescriptiveDirective::class).prop(TestDescriptiveDirective::value).isEqualTo(3)
            }
        }

    @Test
    internal fun `should send the feedback`() = testDispatcherProvider.run {
        // given
        redisFactoryChannel.init(relaxedMockk {
            every { feedbackChannel } returns FEEDBACK_CHANNEL
        })

        // when
        redisFactoryChannel.publishFeedback(EndOfCampaignFeedback("campaign-1", FeedbackStatus.COMPLETED, "the error"))

        // then
        val received = captured.receive()
        assertThat(received).all {
            prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(FEEDBACK_CHANNEL)
            transform("message") { subscriber.subscriberRegistry.serializer.deserialize<Feedback>(it.message) }.isNotNull()
                .isInstanceOf(EndOfCampaignFeedback::class).all {
                    prop(EndOfCampaignFeedback::campaignKey).isEqualTo("campaign-1")
                    prop(EndOfCampaignFeedback::status).isEqualTo(FeedbackStatus.COMPLETED)
                    prop(EndOfCampaignFeedback::error).isEqualTo("the error")
                    prop(EndOfCampaignFeedback::nodeId).isEqualTo("my-node")
                    prop(EndOfCampaignFeedback::tenant).isEqualTo("my-tenant")
                }
        }
    }

    @Test
    internal fun `should send the handshake request`() = testDispatcherProvider.run {
        // when
        val selectorKey = "test-selector-key"
        val selectorValue = "test-selector-value"

        val graphSummary = DirectedAcyclicGraphSummary(name = "new-test-dag-id")
        val newRegistrationScenario = RegistrationScenario(
            name = "new-test-scenario",
            minionsCount = 1,
            directedAcyclicGraphs = listOf(graphSummary)
        )
        val handshakeRequest = HandshakeRequest(
            nodeId = "testNodeId",
            tags = mapOf(selectorKey to selectorValue),
            replyTo = "",
            scenarios = listOf(newRegistrationScenario),
            zone = "by"
        )
        redisFactoryChannel.publishHandshakeRequest(handshakeRequest)

        // then
        val received = captured.receive()
        assertThat(received).all {
            prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(HANDSHAKE_CHANNEL)
            transform("message") { subscriber.subscriberRegistry.serializer.deserialize<HandshakeRequest>(it.message) }.isEqualTo(
                handshakeRequest
            )
        }
    }

    @Test
    internal fun `should send the heartbeat`() = testDispatcherProvider.run {
        // when
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        redisFactoryChannel.publishHeartbeat(
            HEARTBEAT_CHANNEL,
            Heartbeat("the node", "the tenant", now, Heartbeat.State.UNHEALTHY)
        )

        // then
        val received = captured.receive()
        assertThat(received).all {
            prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(HEARTBEAT_CHANNEL)
            transform("message") { subscriber.subscriberRegistry.serializer.deserialize<Heartbeat>(it.message) }.isNotNull()
                .all {
                    prop(Heartbeat::nodeId).isEqualTo("the node")
                    prop(Heartbeat::tenant).isEqualTo("the tenant")
                    prop(Heartbeat::timestamp).isEqualTo(now)
                    prop(Heartbeat::state).isEqualTo(Heartbeat.State.UNHEALTHY)
                }
        }
    }

    companion object {

        const val BROADCAST_CHANNEL = "the-broadcast-channel"

        const val FACTORY_CHANNEL = "the-factory-channel"

        const val FEEDBACK_CHANNEL = "the-feedback-channel"

        const val HANDSHAKE_CHANNEL = "the-handshake-channel"

        const val HEARTBEAT_CHANNEL = "the-heartbeat-channel"
    }
}