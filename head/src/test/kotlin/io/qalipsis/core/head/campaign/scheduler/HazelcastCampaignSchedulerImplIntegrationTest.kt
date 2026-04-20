package io.qalipsis.core.head.campaign.scheduler

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.hazelcast.config.YamlConfigBuilder
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import io.micronaut.core.io.socket.SocketUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignPreparator
import io.qalipsis.core.head.campaign.scheduler.catadioptre.executeCampaign
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.security.TenantProvider
import io.qalipsis.core.head.security.UserProvider
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests for [HazelcastCampaignSchedulerImpl].
 *
 * @author Eric Jess\u00e9
 */
@WithMockk
@Timeout(30)
internal class HazelcastCampaignSchedulerImplIntegrationTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var userProvider: UserProvider

    @RelaxedMockK
    private lateinit var campaignExecutor: CampaignExecutor

    @RelaxedMockK
    private lateinit var tenantProvider: TenantProvider

    @RelaxedMockK
    private lateinit var campaignRepository: CampaignRepository

    @RelaxedMockK
    private lateinit var campaignPreparator: CampaignPreparator

    @MockK
    lateinit var factoryService: FactoryService

    @AfterEach
    fun tearDown() {
        Hazelcast.shutdownAll()
    }

    @Test
    internal fun `should schedule and execute a campaign when delay elapses`() = testDispatcherProvider.runTest {
        // given
        val hazelcastInstance = buildHazelcastInstance()
        val scheduler = createScheduler(hazelcastInstance, this)
        scheduler.setup()

        val latch = CountDownLatch(1)
        coEvery { scheduler.executeCampaign(any()) } answers { latch.countDown() }

        // when
        scheduler.schedule("campaign-key", Instant.now())

        // then
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        coVerify(exactly = 1) { scheduler.executeCampaign("campaign-key") }
        assertThat(hazelcastInstance.getMap<String, String>(SCHEDULE_HANDLERS_MAP)["campaign-key"]).isNull()
    }

    @Test
    internal fun `should cancel the previous schedule when scheduling a new one`() = testDispatcherProvider.runTest {
        // given
        val hazelcastInstance = buildHazelcastInstance()
        val scheduler = createScheduler(hazelcastInstance, this)
        scheduler.setup()

        // Schedule to fire in 2 seconds.
        scheduler.schedule("campaign-key", Instant.now().plusSeconds(2))

        val handlersMap = hazelcastInstance.getMap<String, String>(SCHEDULE_HANDLERS_MAP)
        assertThat(handlersMap["campaign-key"]).isNotNull()

        // Reschedule far in the future, which cancels the original 2-second schedule.
        scheduler.schedule("campaign-key", Instant.now().plusSeconds(60))

        // then - wait longer than the original 2-second delay and verify no execution occurred.
        Thread.sleep(4000)
        coVerify(exactly = 0) { scheduler.executeCampaign(any()) }
        // The handler for the new far-future schedule should still be present.
        assertThat(handlersMap["campaign-key"]).isNotNull()
    }

    @Test
    internal fun `should reschedule a campaign and execute at the new time`() = testDispatcherProvider.runTest {
        // given
        val hazelcastInstance = buildHazelcastInstance()
        val scheduler = createScheduler(hazelcastInstance, this)
        scheduler.setup()

        // Schedule far in the future.
        scheduler.schedule("campaign-key", Instant.now().plusSeconds(60))

        val handlersMap = hazelcastInstance.getMap<String, String>(SCHEDULE_HANDLERS_MAP)
        val oldHandler = handlersMap["campaign-key"]
        assertThat(oldHandler).isNotNull()

        // when - reschedule to fire immediately.
        val latch = CountDownLatch(1)
        coEvery { scheduler.executeCampaign(any()) } answers { latch.countDown() }
        scheduler.schedule("campaign-key", Instant.now())

        // then
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        coVerify(exactly = 1) { scheduler.executeCampaign("campaign-key") }
    }

    @Test
    @Timeout(60)
    internal fun `should execute campaign only once when multiple nodes receive the topic message`() =
        testDispatcherProvider.runTest {
            // given - create a 2-node cluster using TCP-IP discovery with explicit ports.
            val clusterName = "test-cluster-${UUID.randomUUID()}"
            val port1 = SocketUtils.findAvailableTcpPort()
            val port2 = SocketUtils.findAvailableTcpPort()
            // Both instances must be started concurrently. newHazelcastInstance blocks until the node
            // has joined a cluster (or formed standalone), so a sequential start lets node 1 time out
            // on node 2's still-closed port, blacklist it, and bring up a standalone cluster before
            // node 2 ever boots.
            val hz1Future = CompletableFuture.supplyAsync {
                buildClusteredHazelcastInstance(clusterName, port1, listOf("127.0.0.1:$port2"))
            }
            val hz2Future = CompletableFuture.supplyAsync {
                buildClusteredHazelcastInstance(clusterName, port2, listOf("127.0.0.1:$port1"))
            }
            val hz1 = hz1Future.get(30, TimeUnit.SECONDS)
            val hz2 = hz2Future.get(30, TimeUnit.SECONDS)

            assertThat(waitForCluster(expectedSize = 2, hz1, hz2)).isTrue()
            // The IScheduledExecutorService and the RingBuffer backing the ReliableTopic both rely
            // on partition assignments. A freshly formed cluster has no partitions yet assigned, and
            // isClusterSafe returns true vacuously in that state (no migrations to do). Force initial
            // partition arrangement by touching a partitioned data structure on each member, then
            // wait for any resulting migrations to settle before scheduling.
            listOf(hz1, hz2).forEach { it.getMap<String, String>("warmup").size }
            assertThat(waitForClusterSafe(hz1, hz2)).isTrue()

            val scope1 = CoroutineScope(Dispatchers.Default)
            val scope2 = CoroutineScope(Dispatchers.Default)

            val executionCount = AtomicInteger(0)
            val latch = CountDownLatch(1)

            val scheduler1 = createScheduler(hz1, scope1)
            val scheduler2 = createScheduler(hz2, scope2)

            coEvery { scheduler1.executeCampaign(any()) } answers {
                executionCount.incrementAndGet()
                latch.countDown()
            }
            coEvery { scheduler2.executeCampaign(any()) } answers {
                executionCount.incrementAndGet()
                latch.countDown()
            }

            scheduler1.setup()
            scheduler2.setup()
            // Give the ReliableTopic message listeners on both members a moment to attach to the
            // backing Ringbuffer. Without this, a publish that follows setup() too closely can be
            // missed by listeners that have not yet advanced their read cursor.
            Thread.sleep(3000)

            // when - schedule on node 1 with a small delay so the scheduled task is not racing
            // partition assignment.
            scheduler1.schedule("campaign-key", Instant.now().plusMillis(500))

            // then - wait for one execution.
            assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue()
            // Wait extra to ensure no second execution occurs.
            Thread.sleep(3000)
            assertThat(executionCount.get()).isEqualTo(1)
        }

    private fun buildHazelcastInstance(
        clusterName: String = "test-${UUID.randomUUID()}"
    ): HazelcastInstance {
        val config = YamlConfigBuilder(
            this::class.java.classLoader.getResourceAsStream("hazelcast-qalipsis.yml")
        ).build().apply {
            this.clusterName = clusterName
            setProperty("hazelcast.logging.type", "slf4j")
            networkConfig.join.autoDetectionConfig.isEnabled = false
            networkConfig.join.multicastConfig.isEnabled = false
            networkConfig.join.tcpIpConfig.isEnabled = false
        }
        return Hazelcast.newHazelcastInstance(config)
    }

    private fun buildClusteredHazelcastInstance(
        clusterName: String,
        port: Int,
        tcpMembers: List<String>
    ): HazelcastInstance {
        val config = YamlConfigBuilder(
            this::class.java.classLoader.getResourceAsStream("hazelcast-qalipsis.yml")
        ).build().apply {
            this.clusterName = clusterName
            setProperty("hazelcast.logging.type", "slf4j")
            networkConfig.setPort(port)
            networkConfig.isPortAutoIncrement = false
            networkConfig.join.autoDetectionConfig.isEnabled = false
            networkConfig.join.multicastConfig.isEnabled = false
            networkConfig.join.tcpIpConfig.isEnabled = true
            networkConfig.join.tcpIpConfig.setMembers(tcpMembers)
        }
        return Hazelcast.newHazelcastInstance(config)
    }

    private fun createScheduler(
        hazelcastInstance: HazelcastInstance,
        coroutineScope: CoroutineScope
    ): HazelcastCampaignSchedulerImpl {
        return spyk(
            HazelcastCampaignSchedulerImpl(
                hazelcastInstance = hazelcastInstance,
                userProvider = userProvider,
                campaignExecutor = campaignExecutor,
                tenantProvider = tenantProvider,
                campaignRepository = campaignRepository,
                factoryService = factoryService,
                campaignPreparator = campaignPreparator,
                coroutineScope = coroutineScope
            ),
            recordPrivateCalls = true
        )
    }

    private fun waitForClusterSafe(
        vararg instances: HazelcastInstance,
        timeoutMs: Long = 30000
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (instances.all { it.partitionService.isClusterSafe }) {
                return true
            }
            Thread.sleep(200)
        }
        return false
    }

    private fun waitForCluster(
        expectedSize: Int,
        vararg instances: HazelcastInstance,
        timeoutMs: Long = 30000
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (instances.all { it.cluster.members.size >= expectedSize }) {
                return true
            }
            Thread.sleep(200)
        }
        return false
    }

    private companion object {
        const val SCHEDULE_HANDLERS_MAP = "campaign-schedule-handlers"
    }
}
