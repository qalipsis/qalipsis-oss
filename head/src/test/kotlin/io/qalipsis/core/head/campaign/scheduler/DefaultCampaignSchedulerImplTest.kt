package io.qalipsis.core.head.campaign.scheduler

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.api.report.ExecutionStatus.SUCCESSFUL
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignExecutor
import io.qalipsis.core.head.campaign.CampaignPreparator
import io.qalipsis.core.head.factory.FactoryService
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * @author Joël Valère
 */

@WithMockk
@MicronautTest(startApplication = false, environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
internal class DefaultCampaignSchedulerImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var campaignExecutor: CampaignExecutor

    @RelaxedMockK
    private lateinit var tenantRepository: TenantRepository

    @RelaxedMockK
    private lateinit var campaignRepository: CampaignRepository

    @RelaxedMockK
    private lateinit var campaignPreparator: CampaignPreparator

    @RelaxedMockK
    private lateinit var scheduledCampaignsRegistry: ScheduledCampaignsRegistry

    @MockK
    lateinit var factoryService: FactoryService

    private lateinit var defaultCampaignSchedulerImpl: DefaultCampaignSchedulerImpl

    @Test
    internal fun `should retrieve and schedule existing campaigns from database`() = testDispatcherProvider.runTest {
        defaultCampaignSchedulerImpl = spyk(
            DefaultCampaignSchedulerImpl(
                userRepository = userRepository,
                campaignExecutor = campaignExecutor,
                tenantRepository = tenantRepository,
                campaignRepository = campaignRepository,
                factoryService = factoryService,
                campaignPreparator = campaignPreparator,
                scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                coroutineScope = this
            )
        )
        val currentTime = getTimeMock()
        val campaignEntity1 = relaxedMockk<CampaignEntity> {
            every { key } returns "key-1"
            every { start } returns currentTime.minusMillis(10)
        }
        val campaignEntity2 = relaxedMockk<CampaignEntity> {
            every { key } returns "key-2"
            every { start } returns currentTime.minusMillis(10)
        }
        val campaignEntity3 = relaxedMockk<CampaignEntity> {
            every { key } returns "key-3"
            every { start } returns currentTime.plusMillis(10)
        }
        val campaignEntity4 = relaxedMockk<CampaignEntity> {
            every { key } returns "key-4"
            every { start } returns currentTime.plusMillis(10)
        }

        val countLatch = SuspendedCountLatch(4)
        coEvery { campaignRepository.findByResult(refEq(SCHEDULED)) } returns listOf(
            campaignEntity1, campaignEntity2, campaignEntity3, campaignEntity4
        )
        coEvery {
            defaultCampaignSchedulerImpl.schedule(any(), any())
        } coAnswers { countLatch.decrement() }

        // when
        defaultCampaignSchedulerImpl.init()

        // then
        countLatch.await()

        coVerifyOrder {
            campaignRepository.findByResult(refEq(SCHEDULED))
            defaultCampaignSchedulerImpl.schedule(refEq("key-1"), currentTime)
            defaultCampaignSchedulerImpl.schedule(refEq("key-2"), currentTime)
            defaultCampaignSchedulerImpl.schedule(refEq("key-3"), currentTime.plusMillis(10))
            defaultCampaignSchedulerImpl.schedule(refEq("key-4"), currentTime.plusMillis(10))
        }
        confirmVerified(
            userRepository,
            campaignExecutor,
            tenantRepository,
            campaignRepository,
            factoryService,
            campaignPreparator
        )
    }

    @Test
    internal fun `should start a scheduled campaign`() = testDispatcherProvider.runTest {
        defaultCampaignSchedulerImpl = spyk(
            DefaultCampaignSchedulerImpl(
                userRepository = userRepository,
                campaignExecutor = campaignExecutor,
                tenantRepository = tenantRepository,
                campaignRepository = campaignRepository,
                factoryService = factoryService,
                campaignPreparator = campaignPreparator,
                scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                coroutineScope = this
            ),
            recordPrivateCalls = true
        )
        val instant = getTimeMock().plusMillis(5)

        val latch = Latch(true)
        every {
            defaultCampaignSchedulerImpl["schedulingExecution"](refEq("campaign-key"), refEq(instant))
        } coAnswers { latch.release() }

        // when
        defaultCampaignSchedulerImpl.schedule("campaign-key", instant)
        latch.await()

        // then
        coVerifyOrder {
            defaultCampaignSchedulerImpl["schedulingExecution"](refEq("campaign-key"), refEq(instant))
            scheduledCampaignsRegistry.updateSchedule("campaign-key", any<Job>())
        }
        confirmVerified(
            userRepository,
            campaignExecutor,
            tenantRepository,
            campaignRepository,
            factoryService,
            campaignPreparator,
            scheduledCampaignsRegistry
        )

    }

    @Test
    internal fun `should execute a scheduled campaign`() = testDispatcherProvider.runTest {
        defaultCampaignSchedulerImpl = spyk(
            DefaultCampaignSchedulerImpl(
                userRepository = userRepository,
                campaignExecutor = campaignExecutor,
                tenantRepository = tenantRepository,
                campaignRepository = campaignRepository,
                factoryService = factoryService,
                campaignPreparator = campaignPreparator,
                scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                coroutineScope = this
            ),
            recordPrivateCalls = true
        )
        val currentTime = getTimeMock()
        val nextSchedule = currentTime.plusMillis(10)
        val scheduling = mockk<Scheduling> {
            every { nextSchedule(currentTime) } returns nextSchedule
        }
        val instant = currentTime.plusMillis(5)
        val configuration = CampaignConfiguration(
            name = "My new campaign",
            speedFactor = 123.2,
            timeout = Duration.ofSeconds(715),
            hardTimeout = false,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            ),
            scheduledAt = instant,
            scheduling = scheduling
        )
        val campaignEntity = CampaignEntity(
            key = "campaign-key",
            name = "My new campaign",
            speedFactor = 123.0,
            start = currentTime.minusSeconds(173),
            end = currentTime,
            scheduledMinions = 123,
            result = SUCCESSFUL,
            configurer = 1L,
            tenantId = 123L,
            configuration = configuration
        )

        val latch = Latch(true)
        coEvery { campaignRepository.findByKey(refEq("campaign-key")) } returns campaignEntity
        coEvery { userRepository.findUsernameById(1L) } returns "The configurer"
        coEvery { tenantRepository.findReferenceById(123) } returns "my-tenant"
        coEvery { campaignRepository.update(any()) } returnsArgument 0
        coEvery {
            defaultCampaignSchedulerImpl.schedule(refEq("campaign-key"), refEq(nextSchedule))
        } coAnswers { latch.release() }

        // when
        defaultCampaignSchedulerImpl.coInvokeInvisible<Void>("schedulingExecution", "campaign-key", instant)
        latch.await()

        // then
        coVerifyOrder {
            campaignRepository.findByKey(refEq("campaign-key"))
            userRepository.findUsernameById(1L)
            tenantRepository.findReferenceById(123)
            campaignExecutor.start(
                refEq("my-tenant"),
                refEq("The configurer"),
                configuration.copy(name = "My new campaign ($currentTime)")
            )
            campaignRepository.update(withArg {
                assertThat(it).all {
                    prop(CampaignEntity::key).isEqualTo("campaign-key")
                    prop(CampaignEntity::name).isEqualTo("My new campaign")
                    prop(CampaignEntity::speedFactor).isEqualTo(123.0)
                    prop(CampaignEntity::scheduledMinions).isEqualTo(123)
                    prop(CampaignEntity::hardTimeout).isNull()
                    prop(CampaignEntity::softTimeout).isNull()
                    prop(CampaignEntity::configurer).isEqualTo(1L)
                    prop(CampaignEntity::tenantId).isEqualTo(123L)
                    prop(CampaignEntity::configuration).isSameAs(configuration)
                    prop(CampaignEntity::result).isEqualTo(SUCCESSFUL)
                    prop(CampaignEntity::start).isEqualTo(nextSchedule)
                }
            })
            defaultCampaignSchedulerImpl.schedule(refEq("campaign-key"), refEq(nextSchedule))
        }
        confirmVerified(
            userRepository,
            campaignExecutor,
            tenantRepository,
            campaignRepository,
            factoryService,
            campaignPreparator
        )

    }

    @Test
    internal fun `should not execute a scheduled campaign when configuration is null`() =
        testDispatcherProvider.runTest {
            defaultCampaignSchedulerImpl = spyk(
                DefaultCampaignSchedulerImpl(
                    userRepository = userRepository,
                    campaignExecutor = campaignExecutor,
                    tenantRepository = tenantRepository,
                    campaignRepository = campaignRepository,
                    factoryService = factoryService,
                    campaignPreparator = campaignPreparator,
                    scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                    coroutineScope = this
                ),
                recordPrivateCalls = true
            )

            val currentTime = getTimeMock()
            val instant = currentTime.plusMillis(5)
            val campaignEntity = CampaignEntity(
                key = "campaign-key",
                name = "My new campaign",
                speedFactor = 123.0,
                start = currentTime.minusSeconds(173),
                end = currentTime,
                scheduledMinions = 123,
                result = SUCCESSFUL,
                configurer = 1L,
                tenantId = 123L
            )

            coEvery { campaignRepository.findByKey(refEq("campaign-key")) } returns campaignEntity

            // when
            val exception = assertThrows<IllegalArgumentException> {
                defaultCampaignSchedulerImpl.coInvokeInvisible<Void>("schedulingExecution", "campaign-key", instant)
            }

            // then
            assertThat(exception.message).isEqualTo("No configuration was found for the campaign")
            coVerifyOrder {
                campaignRepository.findByKey(refEq("campaign-key"))
            }
            confirmVerified(
                userRepository,
                campaignExecutor,
                tenantRepository,
                campaignRepository,
                factoryService,
                campaignPreparator
            )

        }

    @Test
    internal fun `should not execute a scheduled campaign when configurer does not exist`() =
        testDispatcherProvider.runTest {
            defaultCampaignSchedulerImpl = spyk(
                DefaultCampaignSchedulerImpl(
                    userRepository = userRepository,
                    campaignExecutor = campaignExecutor,
                    tenantRepository = tenantRepository,
                    campaignRepository = campaignRepository,
                    factoryService = factoryService,
                    campaignPreparator = campaignPreparator,
                    scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                    coroutineScope = this
                ),
                recordPrivateCalls = true
            )

            val currentTime = getTimeMock()
            val instant = currentTime.plusMillis(5)

            val configuration = CampaignConfiguration(
                name = "My new campaign",
                speedFactor = 123.2,
                timeout = Duration.ofSeconds(715),
                hardTimeout = false,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                ),
                scheduledAt = instant
            )
            val campaignEntity = CampaignEntity(
                key = "campaign-key",
                name = "My new campaign",
                speedFactor = 123.0,
                start = currentTime.minusSeconds(173),
                end = currentTime,
                scheduledMinions = 123,
                result = SUCCESSFUL,
                configurer = 1L,
                tenantId = 123L,
                configuration = configuration
            )

            coEvery { campaignRepository.findByKey(refEq("campaign-key")) } returns campaignEntity
            coEvery { userRepository.findUsernameById(1L) } returns null

            // when
            val exception = assertThrows<IllegalArgumentException> {
                defaultCampaignSchedulerImpl.coInvokeInvisible<Void>("schedulingExecution", "campaign-key", instant)
            }

            // then
            assertThat(exception.message).isEqualTo("The provided configurer does not exist")
            coVerifyOrder {
                campaignRepository.findByKey(refEq("campaign-key"))
                userRepository.findUsernameById(1L)
            }
            confirmVerified(
                userRepository,
                campaignExecutor,
                tenantRepository,
                campaignRepository,
                factoryService,
                campaignPreparator
            )

        }

    @Test
    internal fun `should schedule a campaign`() = testDispatcherProvider.run {
        defaultCampaignSchedulerImpl = spyk(
            DefaultCampaignSchedulerImpl(
                userRepository = userRepository,
                campaignExecutor = campaignExecutor,
                tenantRepository = tenantRepository,
                campaignRepository = campaignRepository,
                factoryService = factoryService,
                campaignPreparator = campaignPreparator,
                scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                coroutineScope = this
            ),
            recordPrivateCalls = true
        )

        // given
        val scheduleAt = Instant.now().plusSeconds(60)
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            ),
            scheduledAt = scheduleAt
        )

        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { scenarios } returns mapOf(
                "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
            )
        }

        val latch = Latch(true)
        val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
        val scenario2 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-2" }
        val scenario3 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
        coEvery { factoryService.getActiveScenarios(refEq("my-tenant"), setOf("scenario-1", "scenario-2")) } returns
                listOf(scenario1, scenario2, scenario3)
        coEvery {
            campaignPreparator.convertAndSaveCampaign(
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(configuration),
                refEq(true)
            )
        } returns runningCampaign
        coEvery {
            defaultCampaignSchedulerImpl.schedule(
                refEq("my-campaign"),
                refEq(scheduleAt)
            )
        } coAnswers { latch.release() }

        // when
        val result = defaultCampaignSchedulerImpl.schedule("my-tenant", "my-user", configuration)
        latch.await()

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            factoryService.getActiveScenarios(refEq("my-tenant"), setOf("scenario-1", "scenario-2"))
            campaignPreparator.convertAndSaveCampaign(
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(configuration),
                refEq(true)
            )
            defaultCampaignSchedulerImpl.schedule(refEq("my-campaign"), refEq(scheduleAt))
        }
        confirmVerified(
            userRepository,
            campaignExecutor,
            tenantRepository,
            campaignRepository,
            factoryService,
            campaignPreparator
        )
    }

    @Test
    internal fun `should not schedule a campaign when scheduleAt is null`() = testDispatcherProvider.run {
        defaultCampaignSchedulerImpl = spyk(
            DefaultCampaignSchedulerImpl(
                userRepository = userRepository,
                campaignExecutor = campaignExecutor,
                tenantRepository = tenantRepository,
                campaignRepository = campaignRepository,
                factoryService = factoryService,
                campaignPreparator = campaignPreparator,
                scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                coroutineScope = this
            ),
            recordPrivateCalls = true
        )

        // given
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf()
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            defaultCampaignSchedulerImpl.schedule("my-tenant", "my-user", configuration)
        }

        // then
        assertThat(exception.message)
            .isEqualTo("The schedule time should be in the future")
        confirmVerified(
            userRepository,
            campaignExecutor,
            tenantRepository,
            campaignRepository,
            factoryService,
            campaignPreparator
        )
    }

    @Test
    internal fun `should not schedule a campaign when scheduleAt is not in the future`() = testDispatcherProvider.run {
        defaultCampaignSchedulerImpl = spyk(
            DefaultCampaignSchedulerImpl(
                userRepository = userRepository,
                campaignExecutor = campaignExecutor,
                tenantRepository = tenantRepository,
                campaignRepository = campaignRepository,
                factoryService = factoryService,
                campaignPreparator = campaignPreparator,
                scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                coroutineScope = this
            ),
            recordPrivateCalls = true
        )

        // given
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(),
            scheduledAt = Instant.now()
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            defaultCampaignSchedulerImpl.schedule("my-tenant", "my-user", configuration)
        }

        // then
        assertThat(exception.message)
            .isEqualTo("The schedule time should be in the future")
        confirmVerified(
            userRepository,
            campaignExecutor,
            tenantRepository,
            campaignRepository,
            factoryService,
            campaignPreparator
        )
    }

    @Test
    internal fun `should not schedule a campaign when some scenarios are currently not supported`() =
        testDispatcherProvider.runTest {
            defaultCampaignSchedulerImpl = spyk(
                DefaultCampaignSchedulerImpl(
                    userRepository = userRepository,
                    campaignExecutor = campaignExecutor,
                    tenantRepository = tenantRepository,
                    campaignRepository = campaignRepository,
                    factoryService = factoryService,
                    campaignPreparator = campaignPreparator,
                    scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                    coroutineScope = this
                ),
                recordPrivateCalls = true
            )

            // given
            val scheduleAt = Instant.now().plusSeconds(60)
            val configuration = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-1" to ScenarioRequest(1),
                    "scenario-2" to ScenarioRequest(3)
                ),
                scheduledAt = scheduleAt
            )
            val scenario1 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }
            val scenario3 = relaxedMockk<ScenarioSummary> { every { name } returns "scenario-1" }

            coEvery {
                factoryService.getActiveScenarios(
                    refEq("my-tenant"),
                    setOf("scenario-1", "scenario-2")
                )
            } returns listOf(scenario1, scenario3)

            // when
            val exception = assertThrows<IllegalArgumentException> {
                defaultCampaignSchedulerImpl.schedule("my-tenant", "my-user", configuration)
            }

            // then
            assertThat(exception.message)
                .isEqualTo("The scenarios scenario-2 were not found or are not currently supported by healthy factories")
            coVerifyOrder {
                factoryService.getActiveScenarios(refEq("my-tenant"), setOf("scenario-1", "scenario-2"))
            }
            confirmVerified(
                userRepository,
                campaignExecutor,
                tenantRepository,
                campaignRepository,
                factoryService,
                campaignPreparator
            )
        }

    @Test
    internal fun `should update a scheduled campaign and cancel future pre-scheduled tasks`() =
        testDispatcherProvider.run {
            defaultCampaignSchedulerImpl = spyk(
                DefaultCampaignSchedulerImpl(
                    userRepository = userRepository,
                    campaignExecutor = campaignExecutor,
                    tenantRepository = tenantRepository,
                    campaignRepository = campaignRepository,
                    factoryService = factoryService,
                    campaignPreparator = campaignPreparator,
                    scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                    coroutineScope = this
                ),
                recordPrivateCalls = true
            )

            // given
            val campaignKey = "my-campaign"
            val scheduleAt = Instant.now().plusSeconds(60)
            val updateConfiguration = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-7" to ScenarioRequest(3),
                    "scenario-8" to ScenarioRequest(5)
                ),
                scheduledAt = scheduleAt
            )
            val runningCampaign = relaxedMockk<RunningCampaign> {
                every { key } returns "new-campaign-key"
                every { scenarios } returns mapOf(
                    "scenario-7" to relaxedMockk { every { minionsCount } returns 61272 },
                    "scenario-8" to relaxedMockk { every { minionsCount } returns 19921 }
                )
            }
            coEvery {
                defaultCampaignSchedulerImpl.schedule(
                    any(),
                    any(),
                    any()
                )
            } returns runningCampaign
            val campaignEntity = CampaignEntity(
                key = "the-campaign-id",
                name = "This is a campaign",
                speedFactor = 123.0,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                scheduledMinions = 345,
                result = SCHEDULED,
                configurer = 1
            )
            coEvery { campaignRepository.findByTenantAndKeyAndScheduled(any(), any()) } returns campaignEntity

            // when
            val result = defaultCampaignSchedulerImpl.update("my-tenant", "my-user", campaignKey, updateConfiguration)

            // then
            assertThat(result).isSameAs(runningCampaign)
            coVerifyOrder {
                defaultCampaignSchedulerImpl.update(
                    refEq("my-tenant"),
                    refEq("my-user"),
                    refEq(campaignKey),
                    refEq(updateConfiguration)
                )
                campaignRepository.findByTenantAndKeyAndScheduled(refEq("my-tenant"), refEq(campaignKey))
                scheduledCampaignsRegistry.cancelSchedule(refEq(campaignKey))
                defaultCampaignSchedulerImpl.schedule(refEq("my-tenant"), refEq("my-user"), refEq(updateConfiguration))
            }
            confirmVerified(campaignRepository, scheduledCampaignsRegistry, defaultCampaignSchedulerImpl)
        }

    @Test
    internal fun `should throw an exception when a valid scheduled campaign test cannot be found`() =
        testDispatcherProvider.run {
            defaultCampaignSchedulerImpl = spyk(
                DefaultCampaignSchedulerImpl(
                    userRepository = userRepository,
                    campaignExecutor = campaignExecutor,
                    tenantRepository = tenantRepository,
                    campaignRepository = campaignRepository,
                    factoryService = factoryService,
                    campaignPreparator = campaignPreparator,
                    scheduledCampaignsRegistry = scheduledCampaignsRegistry,
                    coroutineScope = this
                ),
                recordPrivateCalls = true
            )

            // given
            val campaignKey = "my-campaign"
            val scheduleAt = Instant.now().plusSeconds(60)
            val configuration = CampaignConfiguration(
                name = "This is a campaign",
                speedFactor = 123.2,
                scenarios = mapOf(
                    "scenario-7" to ScenarioRequest(3),
                    "scenario-8" to ScenarioRequest(5)
                ),
                scheduledAt = scheduleAt
            )
            coEvery { campaignRepository.findByTenantAndKeyAndScheduled(any(), any()) } returns null

            // when
            val caught = assertThrows<IllegalArgumentException> {
                defaultCampaignSchedulerImpl.update(
                    "my-tenant",
                    "my-user",
                    campaignKey,
                    configuration
                )
            }

            // then
            assertThat(caught).all {
                prop(IllegalArgumentException::message).isEqualTo("Campaign does not exist")
            }

            coVerifyOrder {
                defaultCampaignSchedulerImpl.update(
                    refEq("my-tenant"),
                    refEq("my-user"),
                    refEq(campaignKey),
                    refEq(configuration)
                )
                campaignRepository.findByTenantAndKeyAndScheduled(refEq("my-tenant"), refEq(campaignKey))
            }
            confirmVerified(campaignRepository)
        }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }
}