package io.qalipsis.core.head.redis.campaign

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Testcontainers

@ExperimentalLettuceCoroutinesApi
@WithMockk
@Testcontainers
@MicronautTest(environments = [ExecutionEnvironments.REDIS])
internal abstract class AbstractRedisStateIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    protected lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    protected lateinit var factoryService: FactoryService

    @RelaxedMockK
    protected lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    protected lateinit var campaign: CampaignConfiguration

    @Inject
    protected lateinit var operations: CampaignRedisOperations

    @Inject
    protected lateinit var redisCommands: RedisCoroutinesCommands<String, String>

    @MockBean(FactoryService::class)
    fun factoryService(): FactoryService = factoryService

    @BeforeEach
    internal fun setUp() {
        campaign = spyk(
            CampaignConfiguration(
                id = "my-campaign",
                broadcastChannel = "my-broadcast-channel"
            )
        )

        every { idGenerator.short() } returnsMany (1..10).map { "the-directive-$it" }
    }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        redisCommands.flushdb()
    }
}