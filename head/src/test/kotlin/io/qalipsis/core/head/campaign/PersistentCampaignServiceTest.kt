package io.qalipsis.core.head.campaign

import assertk.assertThat
import assertk.assertions.isSameAs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
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

    @RelaxedMockK
    private lateinit var tenantRepository: TenantRepository

    @RelaxedMockK
    private lateinit var campaignConverter: CampaignConverter

    @InjectMockKs
    private lateinit var persistentCampaignService: PersistentCampaignService

    @Test
    internal fun `should save the new campaign`() = testDispatcherProvider.run {
        // given
        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
        val now = getTimeMock()
        val campaign = CampaignConfiguration(
            tenant = "my-tenant",
            key = "my-campaign",
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
        val convertedCampaign = relaxedMockk<Campaign>()
        coEvery { campaignConverter.convertToModel(any()) } returns convertedCampaign
        val savedEntity = relaxedMockk<CampaignEntity> {
            every { id } returns 8126
        }
        coEvery { campaignRepository.save(any()) } returns savedEntity
        coEvery { userRepository.findIdByUsername("qalipsis-user") } returns 199

        // when
        val result = persistentCampaignService.create("qalipsis-user", "This is a campaign", campaign)

        // then
        assertThat(result).isSameAs(convertedCampaign)
        coVerifyOrder {
            userRepository.findIdByUsername("qalipsis-user")
            campaignRepository.save(
                CampaignEntity(
                    key = "my-campaign",
                    name = "This is a campaign",
                    speedFactor = 123.2,
                    start = now,
                    configurer = 199,
                    tenantId = 8165L
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", 6272),
                    CampaignScenarioEntity(8126, "scenario-2", 12321)
                )
            )
            campaignConverter.convertToModel(refEq(savedEntity))
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository)
    }

    @Test
    internal fun `should close the running campaign`() = testDispatcherProvider.run {
        // given
        val campaignEntity = relaxedMockk<CampaignEntity>()
        coEvery { campaignRepository.findByKey("my-tenant", "my-campaign") } returns campaignEntity
        val convertedCampaign = relaxedMockk<Campaign>()
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns convertedCampaign

        // when
        val result = persistentCampaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED)

        // then
        assertThat(result).isSameAs(convertedCampaign)
        coVerifyOnce {
            campaignRepository.close("my-tenant", "my-campaign", ExecutionStatus.FAILED)
            campaignRepository.findByKey("my-tenant", "my-campaign")
            campaignConverter.convertToModel(refEq(campaignEntity))
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
}