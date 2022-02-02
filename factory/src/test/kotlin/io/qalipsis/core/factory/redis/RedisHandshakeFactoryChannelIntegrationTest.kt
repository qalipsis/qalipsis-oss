package io.qalipsis.core.factory.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.handshake.HandshakeResponse
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.concurrent.CountDownLatch

@ExperimentalLettuceCoroutinesApi
@WithMockk
@Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
internal class RedisHandshakeFactoryChannelIntegrationTest : AbstractRedisIntegrationTest() {

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
    private lateinit var factoryConfiguration: FactoryConfiguration

    @BeforeEach
    fun setUp() {
        every { factoryConfiguration.handshakeRequestChannel } returns "handshake"
        every { factoryConfiguration.handshakeResponseChannel } returns "handshake-response"
        every { factoryConfiguration.nodeId } returns "1"
    }

    @Test
    @Timeout(10)
    internal fun `should consume handshake response only if handshakeNodeId is the same from factory configuration nodeid`() = testDispatcherProvider.run {
        val response1 = HandshakeResponse("2", "2", "unicast", "broadcast", "feedback", "heartbeat", Duration.ofMillis(100))

        val response2 = HandshakeResponse("1", "2", "unicast", "broadcast", "feedback", "heartbeat", Duration.ofMillis(100))

        redisCoroutinesCommands.xadd(factoryConfiguration.handshakeResponseChannel, mapOf("1" to distributionSerializer.serialize(response1).decodeToString()))
        redisCoroutinesCommands.xadd(factoryConfiguration.handshakeResponseChannel, mapOf("1" to distributionSerializer.serialize(response2).decodeToString()))
        var handShakeResponse: HandshakeResponse? = null
        val countDownLatch = CountDownLatch(1)


        val subscriberId = idGenerator.short()
        val handshakeFactoryChannel = RedisHandshakeFactoryChannel(this, distributionSerializer, redisCoroutinesCommands, idGenerator, factoryConfiguration)
        handshakeFactoryChannel.onReceiveResponse(subscriberId) {
            handShakeResponse = it
            countDownLatch.countDown()
        }

        withContext(Dispatchers.IO) {
            countDownLatch.await()
        }

        Assertions.assertEquals(response2, handShakeResponse)
    }

    @Test
    @Timeout(10)
    fun `should send request handshake`() = testDispatcherProvider.run {
        val handshakeFactoryChannel = RedisHandshakeFactoryChannel(this, distributionSerializer, redisCoroutinesCommands, idGenerator, factoryConfiguration)
        val nodeId = idGenerator.short()
        val handshakeRequestSent = HandshakeRequest(nodeId, emptyMap(), "test", emptyList())

        handshakeFactoryChannel.send(handshakeRequestSent)
        val message = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from(factoryConfiguration.handshakeRequestChannel, "0")).first()

        Assertions.assertEquals(handshakeRequestSent, distributionSerializer.deserialize(message.body.values.first().encodeToByteArray()))
    }
}
