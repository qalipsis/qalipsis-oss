package io.qalipsis.core.head.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.concurrent.CountDownLatch

@ExperimentalLettuceCoroutinesApi
@Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
@WithMockk
internal class RedisHeartbeatConsumerChannelIntegrationTest : AbstractRedisIntegrationTest() {

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
    internal fun setUp() {
        every { headConfiguration.heartbeatConsumerGroupName } returns "test"
    }

    @Test
    @Timeout(20)
    internal fun `should consumer the later produced heartbeat`() = testDispatcherProvider.run {
        val heartbeat = Heartbeat(idGenerator.short(), Instant.now())
        redisCoroutinesCommands.xadd("test-channel", mapOf("test" to distributionSerializer.serialize(heartbeat).decodeToString()))

        val latch = CountDownLatch(1)
        val consumer = RedisHeartbeatConsumerChannel(this, distributionSerializer, redisCoroutinesCommands, idGenerator, headConfiguration)

        var consumedHeartbeat: Heartbeat? = null
        consumer.start("test-channel") {
            consumedHeartbeat = it
            latch.countDown()
        }

        withContext(Dispatchers.IO) {
            latch.await()
        }
        assertNotNull(consumedHeartbeat)
        assertThat(consumedHeartbeat).all{
            prop(Heartbeat::timestamp).transform { it.toEpochMilli() }.isEqualTo(heartbeat.timestamp.toEpochMilli())
            prop(Heartbeat::nodeId).isEqualTo(heartbeat.nodeId)
            prop(Heartbeat::campaignId).isEqualTo(heartbeat.campaignId)
            prop(Heartbeat::state).isEqualTo(heartbeat.state)
        }
    }
}