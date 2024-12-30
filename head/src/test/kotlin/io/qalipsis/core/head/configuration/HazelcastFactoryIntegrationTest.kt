package io.qalipsis.core.head.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.hazelcast.cluster.Address
import com.hazelcast.cluster.Member
import com.hazelcast.config.ScheduledExecutorConfig
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.time.Duration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@WithMockk
@PropertySource(
    Property(name = "hazelcast.properties.hazelcast.heartbeat.failuredetector.type", value = "deadline"),
    Property(name = "hazelcast.properties.hazelcast.heartbeat.interval.seconds", value = "2"),
    Property(name = "hazelcast.properties.hazelcast.max.no.heartbeat.seconds", value = "60"),
    Property(name = "hazelcast.properties.hazelcast.diagnostics.enabled", value = "true")
)
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.HEAD], startApplication = false)
internal class HazelcastFactoryIntegrationTest : AbstractRedisIntegrationTest() {

    @Inject
    lateinit var hazelcastFactory: HazelcastFactory

    @Test
    fun `should build a cluster with tcp configuration`() {
        // given
        val port1 = SocketUtils.findAvailableTcpPort()
        hazelcastFactory.buildHazelcastInstance(mockk {
            every { port } returns port1
            every { scheduledExecutorService } returns mockk {
                every { statisticsEnabled } returns false
                every { poolSize } returns 2
                every { durability } returns 1
                every { capacity } returns 10
                every { capacityPolicy } returns ScheduledExecutorConfig.CapacityPolicy.PER_NODE
                every { mergePolicy } returns null
            }
            every { discoveryStrategy } returns HazelcastConfiguration.DiscoveryStrategy.TCP_IP_REDIS
            every { tcpIp } returns mockk {
                every { timeout } returns Duration.ofSeconds(10)
                every { connectionTimeout } returns Duration.ofSeconds(6)
            }
        })

        // when
        val port2 = SocketUtils.findAvailableTcpPort()
        val secondMember = hazelcastFactory.buildHazelcastInstance(mockk {
            every { port } returns port2
            every { scheduledExecutorService } returns mockk {
                every { statisticsEnabled } returns false
                every { poolSize } returns 2
                every { durability } returns 1
                every { capacity } returns 10
                every { capacityPolicy } returns ScheduledExecutorConfig.CapacityPolicy.PER_NODE
                every { mergePolicy } returns null
            }
            every { discoveryStrategy } returns HazelcastConfiguration.DiscoveryStrategy.TCP_IP_REDIS
            every { tcpIp } returns mockk {
                every { timeout } returns Duration.ofSeconds(10)
                every { connectionTimeout } returns Duration.ofSeconds(6)
            }
        })

        // then
        assertThat(secondMember.cluster.members).all {
            hasSize(2)
            any {
                it.prop(Member::getAddress).prop(Address::getPort).isEqualTo(port1)
            }
            any {
                it.prop(Member::getAddress).prop(Address::getPort).isEqualTo(port2)
            }
        }
    }

}