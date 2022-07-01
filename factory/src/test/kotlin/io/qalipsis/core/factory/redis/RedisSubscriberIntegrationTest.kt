package io.qalipsis.core.factory.redis

import io.aerisconsulting.catadioptre.setProperty
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.configuration.RedisPubSubConfiguration
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.directives.TestSingleUseDirective
import io.qalipsis.core.factory.communication.DirectiveListener
import io.qalipsis.core.factory.communication.HandshakeResponseListener
import io.qalipsis.core.handshake.HandshakeResponse
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
        redisFactoryChannel.subscribedHandshakeResponseChannels.clear()
        redisFactoryChannel.subscribedDirectiveChannels.clear()
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
        val directive = TestSingleUseDirective()
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
    internal fun `should receive the handshake response`() = testDispatcherProvider.run {
        // given
        val countLatch = SuspendedCountLatch(2)
        coEvery { handshakeResponseListener1.notify(any()) } coAnswers { countLatch.decrement() }
        coEvery { handshakeResponseListener2.notify(any()) } coAnswers { countLatch.decrement() }

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

    companion object {

        const val BROADCAST_CHANNEL = "the-broadcast-channel"

        const val FACTORY_CHANNEL = "the-factory-channel"

        const val FACTORY_CHANNEL_2 = "the-factory-channel-2"

        const val HANDSHAKE_RESPONSE_CHANNEL = "the-handshake-response-channel"
    }
}