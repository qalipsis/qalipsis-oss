package io.qalipsis.core.head.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.concurrent.CountDownLatch

@ExperimentalLettuceCoroutinesApi
@WithMockk
@Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
internal class RedisHandshakeHeadChannelIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    lateinit var redisCoroutinesCommands: RedisCoroutinesCommands<String, String>

    @Inject
    lateinit var distributionSerializer: DistributionSerializer

    @Inject
    lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @BeforeEach
    fun setUp() {
        every { headConfiguration.handshakeRequestChannel } returns "handshake"
    }

    @Test
    @Timeout(20)
    internal fun `should consume handshake from factories`() = testDispatcherProvider.run {
        val nodeId = idGenerator.short()
        val handshakeRequestSent = HandshakeRequest(nodeId, emptyMap(), "test", emptyList())
        redisCoroutinesCommands.xadd(headConfiguration.handshakeRequestChannel, mapOf(nodeId to distributionSerializer.serialize(handshakeRequestSent).decodeToString()))
        var handshakeRequestReceived: HandshakeRequest? = null
        val countDownLatch = CountDownLatch(1)


        val subscriberId = idGenerator.short()
        val handshakeHeadChannel = RedisHandshakeHeadChannel(this, distributionSerializer, redisCoroutinesCommands, idGenerator, headConfiguration)
        handshakeHeadChannel.onReceiveRequest(subscriberId) {
            handshakeRequestReceived = it
            countDownLatch.countDown()
        }

        withContext(Dispatchers.IO) {
            countDownLatch.await()
        }

        assertEquals(handshakeRequestSent, handshakeRequestReceived)
    }

    @Test
    @Timeout(10)
    fun `should send response handshake from request`() = testDispatcherProvider.run {
        val handshakeHeadChannel = RedisHandshakeHeadChannel(this, distributionSerializer, redisCoroutinesCommands, idGenerator, headConfiguration)
        val response = HandshakeResponse("1", "2", "unicast", "broadcast", "feedback", "heartbeat", Duration.ofMillis(100))
        handshakeHeadChannel.sendResponse("test-channel", response)
        val message = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("test-channel", "0")).first()

        assertEquals(response, distributionSerializer.deserialize(message.body.values.first().encodeToByteArray()))
    }
}