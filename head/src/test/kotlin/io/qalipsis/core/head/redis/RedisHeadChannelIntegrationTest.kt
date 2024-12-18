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
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.campaign.CampaignService
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
import java.time.Duration

@ExperimentalLettuceCoroutinesApi
@WithMockk
@PropertySource(
    Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS),
    Property(name = "head.handshake-request-channel", value = RedisSubscriberIntegrationTest.HANDSHAKE_REQUEST_CHANNEL),
    Property(name = "head.heartbeat-channel", value = RedisSubscriberIntegrationTest.HEARTBEAT_CHANNEL)
)
@MicronautTest(
    environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD],
    startApplication = false
)
internal class RedisHeadChannelIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var redisHeadChannel: RedisHeadChannel

    @Inject
    private lateinit var subscriber: RedisSubscriber

    @Inject
    private lateinit var directiveRegistry: DirectiveRegistry

    @Inject
    @field:Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME)
    lateinit var subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>

    private lateinit var captured: Channel<ChannelMessage<String, ByteArray>>

    @MockBean(CampaignService::class)
    fun campaignService() = relaxedMockk<CampaignService>()


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
        redisHeadChannel.publishDirective(TestDescriptiveDirective(1, FACTORY_CHANNEL))

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
        redisHeadChannel.publishDirective(
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
                .transform { runBlocking { directiveRegistry.get(it) } }.isNotNull()
                .isInstanceOf(MinionsDeclarationDirective::class).all {
                    prop(MinionsDeclarationDirective::campaignKey).isEqualTo("the campaign")
                    prop(MinionsDeclarationDirective::scenarioName).isEqualTo("the scenario")
                    prop(MinionsDeclarationDirective::minionsCount).isEqualTo(1000)
                }
        }
    }

    @Test
    internal fun `should send the handshake response`() = testDispatcherProvider.run {
        // when
        val handshakeResponse = HandshakeResponse(
            handshakeNodeId = "the-node-id",
            nodeId = "the-actual-node-id",
            unicastChannel = "the-actual-channel",
            heartbeatChannel = "the-heartbeat-channel",
            heartbeatPeriod = Duration.ofMinutes(1)
        )
        redisHeadChannel.publishHandshakeResponse(HANDSHAKE_CHANNEL, handshakeResponse)

        // then
        val received = captured.receive()
        assertThat(received).all {
            prop(ChannelMessage<String, ByteArray>::getChannel).isEqualTo(HANDSHAKE_CHANNEL)
            transform("message") { subscriber.subscriberRegistry.serializer.deserialize<HandshakeResponse>(it.message) }.isEqualTo(
                handshakeResponse
            )
        }
    }

    companion object {

        const val FACTORY_CHANNEL = "the-factory-channel"

        const val HANDSHAKE_CHANNEL = "the-handshake-channel"
    }
}