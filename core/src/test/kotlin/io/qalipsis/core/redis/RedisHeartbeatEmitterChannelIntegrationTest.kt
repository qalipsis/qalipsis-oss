package io.qalipsis.core.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.matchesPredicate
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 * @author Gabriel Moraes
 */
@ExperimentalLettuceCoroutinesApi
@Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
internal class RedisHeartbeatEmitterChannelIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    lateinit var redisCoroutinesCommands: RedisCoroutinesCommands<String, String>

    @Inject
    lateinit var distributionSerializer: DistributionSerializer

    @Inject
    lateinit var idGenerator: IdGenerator

    @Test
    @Timeout(10)
    internal fun `should emmit heartbeat every period of duration`() = testDispatcherProvider.run {

        val emitter = RedisHeartbeatEmitterChannel(this, distributionSerializer, redisCoroutinesCommands)
        val factoryNodeId = idGenerator.short()
        emitter.start(factoryNodeId, "test-channel", Duration.ofMillis(10))
        delay(1000)

        val messageCounter: Int = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("test-channel", "0")).count()

        assertThat(messageCounter).matchesPredicate { it > 1 }
    }

    @Test
    @Timeout(10)
    internal fun `should emit UNREGISTERED heartbeat on closeChannels`() = testDispatcherProvider.run {

        val emitter = RedisHeartbeatEmitterChannel(this, distributionSerializer, redisCoroutinesCommands)
        val factoryNodeId = idGenerator.short()
        emitter.start(factoryNodeId, "test-channel", Duration.ofMillis(100000))
        emitter.closeChannels()
        delay(1000)

        val firstMessage = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("test-channel", "0")).filter {
            value -> (distributionSerializer.deserialize(value.body.values.iterator().next().toByteArray()) as Heartbeat).state == Heartbeat.STATE.UNREGISTERED
        }.first().body.values.first()

        val deserializedMessage = distributionSerializer.deserialize<Heartbeat>(firstMessage.toByteArray())
        assertThat(deserializedMessage).all {
            prop("state") { Heartbeat::state.call(it) }.isEqualTo(Heartbeat.STATE.UNREGISTERED)
            prop("nodeId") { Heartbeat::nodeId.call(it) }.isEqualTo(factoryNodeId)
        }
    }
}
