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

import io.aerisconsulting.catadioptre.setProperty
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@ExperimentalLettuceCoroutinesApi
@WithMockk
@Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.FACTORY], startApplication = false)
internal class RedisSubscriberIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK(name = "directiveListener1")
    private lateinit var directiveListener1: DirectiveListener<Directive>

    @RelaxedMockK(name = "directiveListener2")
    private lateinit var directiveListener2: DirectiveListener<Directive>

    @RelaxedMockK(name = "directiveListener3")
    private lateinit var directiveListener3: DirectiveListener<Directive>

    @RelaxedMockK(name = "handshakeResponseListener1")
    private lateinit var handshakeResponseListener1: HandshakeResponseListener

    @RelaxedMockK(name = "handshakeResponseListener2")
    private lateinit var handshakeResponseListener2: HandshakeResponseListener

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    @Inject
    private lateinit var redisFactoryChannel: RedisFactoryChannel

    @Inject
    private lateinit var redisSubscriber: RedisSubscriber

    @Inject
    @field:Named(RedisPubSubConfiguration.PUBLISHER_BEAN_NAME)
    private lateinit var publisherCommands: RedisPubSubReactiveCommands<String, ByteArray>

    @Inject
    @field:Named(RedisPubSubConfiguration.SUBSCRIBER_BEAN_NAME)
    private lateinit var subscriberCommands: RedisPubSubReactiveCommands<String, ByteArray>

    @MockBean(FactoryConfiguration::class)
    fun factoryConfiguration() = factoryConfiguration

    @Inject
    private lateinit var serializer: DistributionSerializer

    private var initialized = false

    @BeforeEach
    internal fun setUp() {
        if (!initialized) {
            initialized = true

            redisSubscriber.setProperty(
                "directiveListeners",
                listOf(directiveListener1, directiveListener2, directiveListener3)
            )
            redisSubscriber.setProperty(
                "handshakeResponseListeners",
                listOf(handshakeResponseListener1, handshakeResponseListener2)
            )
        }
        redisSubscriber.init()
    }

    @AfterEach
    internal fun tearDown() {
        redisSubscriber.subscribedHandshakeResponseChannels.clear()
        redisSubscriber.subscribedDirectiveChannels.clear()
        redisSubscriber.close()
        subscriberCommands.quit().toFuture().get()
        connection.sync().flushdb()
    }

    @Test
    @Timeout(5)
    internal fun `should receive the broadcasted directive`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(2)
        every { directiveListener1.accept(any()) } returns true
        every { directiveListener2.accept(any()) } returns false
        every { directiveListener3.accept(any()) } returns true
        coEvery { directiveListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { directiveListener3.notify(any()) } coAnswers { countLatch.decrement() }

        // when
        redisFactoryChannel.init(relaxedMockk {
            every { broadcastChannel } returns BROADCAST_CHANNEL
        })

        // when
        val directive = TestDescriptiveDirective(1)
        redisFactoryChannel.publishDirective(directive)
        countLatch.await()

        // then
        coVerifyOnce {
            directiveListener1.accept(eq(directive))
            directiveListener2.accept(eq(directive))
            directiveListener3.accept(eq(directive))
            directiveListener1.notify(eq(directive))
            directiveListener3.notify(eq(directive))
        }
        confirmVerified(
            directiveListener1,
            directiveListener2,
            directiveListener3,
            handshakeResponseListener1,
            handshakeResponseListener2
        )
    }

    @Test
    @Timeout(5)
    internal fun `should consume the newly subscribed channels`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(4)
        every { directiveListener1.accept(any()) } returns true
        every { directiveListener2.accept(any()) } returns false
        every { directiveListener3.accept(any()) } returns true
        coEvery { directiveListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { directiveListener3.notify(any()) } coAnswers { countLatch.decrement() }

        // when
        redisFactoryChannel.publishDirective(FACTORY_CHANNEL, TestDescriptiveDirective(1))
        redisFactoryChannel.publishDirective(FACTORY_CHANNEL_2, TestDescriptiveDirective(1))
        redisFactoryChannel.subscribeDirective(FACTORY_CHANNEL)
        redisFactoryChannel.publishDirective(FACTORY_CHANNEL, TestDescriptiveDirective(2))
        redisFactoryChannel.subscribeDirective(FACTORY_CHANNEL_2)
        redisFactoryChannel.publishDirective(FACTORY_CHANNEL_2, TestDescriptiveDirective(3))
        countLatch.await()

        // then
        coVerifyOnce {
            directiveListener1.accept(eq(TestDescriptiveDirective(2)))
            directiveListener2.accept(eq(TestDescriptiveDirective(2)))
            directiveListener3.accept(eq(TestDescriptiveDirective(2)))
            directiveListener1.notify(eq(TestDescriptiveDirective(2)))
            directiveListener3.notify(eq(TestDescriptiveDirective(2)))

            directiveListener1.accept(eq(TestDescriptiveDirective(3)))
            directiveListener2.accept(eq(TestDescriptiveDirective(3)))
            directiveListener3.accept(eq(TestDescriptiveDirective(3)))
            directiveListener1.notify(eq(TestDescriptiveDirective(3)))
            directiveListener3.notify(eq(TestDescriptiveDirective(3)))
        }
    }

    @Test
    @Timeout(5)
    internal fun `should receive the broadcasted single use directive`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(2)
        every { directiveListener1.accept(any()) } returns true
        every { directiveListener2.accept(any()) } returns false
        every { directiveListener3.accept(any()) } returns true
        coEvery { directiveListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { directiveListener3.notify(any()) } coAnswers { countLatch.decrement() }

        // when
        redisFactoryChannel.init(relaxedMockk {
            every { broadcastChannel } returns BROADCAST_CHANNEL
        })

        // when
        val directive = MinionsDeclarationDirective("campaign", "scenario", 1, channel = BROADCAST_CHANNEL)
        redisFactoryChannel.publishDirective(directive)
        countLatch.await()

        // then
        coVerifyOnce {
            directiveListener1.accept(neq(directive))
            directiveListener2.accept(neq(directive))
            directiveListener3.accept(neq(directive))
            directiveListener1.notify(eq(directive))
            directiveListener3.notify(eq(directive))
        }
        confirmVerified(
            directiveListener1,
            directiveListener2,
            directiveListener3,
            handshakeResponseListener1,
            handshakeResponseListener2
        )
    }

    @Test
    @Timeout(5)
    internal fun `should receive the handshake response when the node IDs match`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(2)
        coEvery { handshakeResponseListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { handshakeResponseListener2.notify(any()) } coAnswers { countLatch.decrement() }
        every { factoryConfiguration.nodeId } returns "the-handshake-node-id"

        // when
        redisFactoryChannel.subscribeHandshakeResponse(HANDSHAKE_RESPONSE_CHANNEL)
        val handshakeResponse = HandshakeResponse(
            handshakeNodeId = "the-handshake-node-id",
            nodeId = "the-node-id",
            unicastChannel = "the-unicast",
            heartbeatChannel = "the-heartbeat",
            heartbeatPeriod = Duration.ofSeconds(2)
        )
        publisherCommands.publish(HANDSHAKE_RESPONSE_CHANNEL, serializer.serialize(handshakeResponse)).subscribe()
        countLatch.await()

        // then
        coVerifyOnce {
            handshakeResponseListener1.notify(eq(handshakeResponse))
            handshakeResponseListener2.notify(eq(handshakeResponse))
        }
        confirmVerified(
            directiveListener1,
            directiveListener2,
            directiveListener3,
            handshakeResponseListener1,
            handshakeResponseListener2
        )
    }

    @Test
    @Timeout(5)
    internal fun `should ignore the handshake response when the node IDs do not match`() = testDispatcherProvider.run {
        // given
        every { factoryConfiguration.nodeId } returns "the-handshake-node-id"

        // when
        redisFactoryChannel.subscribeHandshakeResponse(HANDSHAKE_RESPONSE_CHANNEL)
        val handshakeResponse = HandshakeResponse(
            handshakeNodeId = "the-different-handshake-node-id",
            nodeId = "the-node-id",
            unicastChannel = "the-unicast",
            heartbeatChannel = "the-heartbeat",
            heartbeatPeriod = Duration.ofSeconds(2)
        )
        publisherCommands.publish(HANDSHAKE_RESPONSE_CHANNEL, serializer.serialize(handshakeResponse)).subscribe()
        delay(500)

        // then
        confirmVerified(
            directiveListener1,
            directiveListener2,
            directiveListener3,
            handshakeResponseListener1,
            handshakeResponseListener2
        )
    }

    companion object {

        const val BROADCAST_CHANNEL = "the-broadcast-channel"

        const val FACTORY_CHANNEL = "the-factory-channel"

        const val FACTORY_CHANNEL_2 = "the-factory-channel-2"

        const val HANDSHAKE_RESPONSE_CHANNEL = "the-handshake-response-channel"
    }
}