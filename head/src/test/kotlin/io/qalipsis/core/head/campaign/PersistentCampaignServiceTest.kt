package io.qalipsis.core.head.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@WithMockk
internal class PersistentCampaignServiceTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignRepository: CampaignRepository

    @RelaxedMockK
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var persistentCampaignService: PersistentCampaignService

    @Test
    internal fun `should save the new campaign`() = testDispatcherProvider.run {
        // given
        val now = getTimeMock()
        val campaign = CampaignConfiguration(
            name = "my-campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioConfiguration(
                    6272
                ),
                "scenario-2" to ScenarioConfiguration(
                    12321
                )
            )
        )
        coEvery { campaignRepository.save(any()) } returns mockk { every { id } returns 8126 }
        coEvery { userRepository.findIdByUsername("qalipsis-user") } returns 199

        // when
        persistentCampaignService.create("qalipsis-user", campaign)

        // then
        coVerifyOrder {
            userRepository.findIdByUsername("qalipsis-user")
            campaignRepository.save(
                CampaignEntity(
                    campaignName = "my-campaign",
                    speedFactor = 123.2,
                    start = now,
                    configurer = 199
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", 6272),
                    CampaignScenarioEntity(8126, "scenario-2", 12321)
                )
            )
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository)
    }

    @Test
    internal fun `should close the running campaign`() = testDispatcherProvider.run {
        // when
        persistentCampaignService.close("my-campaign", ExecutionStatus.FAILED)

        // then
        coVerifyOnce {
            campaignRepository.close("my-campaign", ExecutionStatus.FAILED)
        }
        confirmVerified(campaignRepository, campaignScenarioRepository)
    }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }


    @Test
    internal fun `should returns the searched campaigns from the repository with sorting null`() = testDispatcherProvider.run {
        // given
        val campaign1 = relaxedMockk<CampaignEntity>()
        val campaign2 = relaxedMockk<CampaignEntity>()
        val campaigns = listOf(campaign1, campaign2)
        coEvery { campaignRepository.findAll("my-tenant") } returns campaigns

        // when
        val result = persistentCampaignService.getAllCampaigns("my-tenant", null, null)

        // then
        assertThat(result).all {
            containsOnly(campaign1, campaign2)
            hasSize(2)
            isEqualTo(campaigns)
        }
        coVerify {
            campaignRepository.findAll("my-tenant")
        }
        confirmVerified(campaignRepository)
    }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting asc`() = runBlockingTest {
        // given
        val campaign1 = relaxedMockk<CampaignEntity>()
        val campaign2 = relaxedMockk<CampaignEntity>()
        val campaigns = listOf(campaign1, campaign2)
        coEvery { campaignRepository.findAll("my-tenant") } returns campaigns

        // when
        val result = persistentCampaignService.getAllCampaigns("my-tenant", null, "name:asc")

        // then
        assertThat(result).all {
            containsOnly(campaign1, campaign2)
            hasSize(2)
            isEqualTo(campaigns)
        }
        coVerify {
            campaignRepository.findAll("my-tenant")
        }
        confirmVerified(campaignRepository)
    }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting desc`() = runBlockingTest {
        // given
        val campaign1 = relaxedMockk<CampaignEntity>()
        val campaign2 = relaxedMockk<CampaignEntity>()
        val campaigns = listOf(campaign1, campaign2)
        coEvery { campaignRepository.findAll("my-tenant") } returns campaigns

        // when
        val result = persistentCampaignService.getAllCampaigns("my-tenant", null, "name:desc")

        // then
        assertThat(result).all {
            containsOnly(campaign1, campaign2)
            hasSize(2)
            isEqualTo(campaigns.reversed())
        }
        coVerify {
            campaignRepository.findAll("my-tenant")
        }
        confirmVerified(campaignRepository)
    }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting`() = runBlockingTest {
        // given
        val campaign1 = relaxedMockk<CampaignEntity>()
        val campaign2 = relaxedMockk<CampaignEntity>()
        val campaigns = listOf(campaign1, campaign2)
        coEvery { campaignRepository.findAll("my-tenant") } returns campaigns

        // when
        val result = persistentCampaignService.getAllCampaigns("my-tenant", null, "name")

        // then
        assertThat(result).all {
            containsOnly(campaign1, campaign2)
            hasSize(2)
            isEqualTo(campaigns)
        }
        coVerify {
            campaignRepository.findAll("my-tenant")
        }
        confirmVerified(campaignRepository)
    }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting and filtering`() = runBlockingTest {
        // given
        val campaign1 = relaxedMockk<CampaignEntity>()
        val campaign2 = relaxedMockk<CampaignEntity>()
        val campaigns = listOf(campaign1, campaign2)
        val filters =  listOf("test-1", "test-2")
        coEvery { campaignRepository.findAll("my-tenant", filters) } returns campaigns

        // when
        val result = persistentCampaignService.getAllCampaigns("my-tenant", "test-1, test-2", "name")

        // then
        assertThat(result).all {
            containsOnly(campaign1, campaign2)
            hasSize(2)
            isEqualTo(campaigns)
        }
        coVerify {
            campaignRepository.findAll("my-tenant", filters)
        }
        confirmVerified(campaignRepository)
    }
}