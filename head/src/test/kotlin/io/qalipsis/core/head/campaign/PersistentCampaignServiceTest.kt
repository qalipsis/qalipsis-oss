package io.qalipsis.core.head.campaign

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
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
    internal fun `should create the new campaign`() = testDispatcherProvider.run {
        // given
        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
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
                    configurer = 199,
                    tenantId = 8165L
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(8126, "scenario-2", minionsCount = 12321)
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
            campaignRepository.complete("my-tenant", "my-campaign", ExecutionStatus.FAILED)
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


    @Test
    internal fun `should returns the searched campaigns from the repository with default sorting`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("start")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), pageable, 2)
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), null, 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }
            confirmVerified(campaignRepository, campaignConverter)
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting asc`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), "name:asc", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }
            confirmVerified(campaignRepository, campaignConverter)
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting desc`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), "name:desc", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }
            confirmVerified(campaignRepository, campaignConverter)
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", emptyList(), "name", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }
            confirmVerified(campaignRepository, campaignConverter)
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting and filtering`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val filter1 = "%test%"
            val filter2 = "%he%lo%"
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)
            coEvery { campaignRepository.findAll("my-tenant", listOf(filter1, filter2), pageable) } returns page

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignConverter.convertToModel(any()) } returns campaign1 andThen campaign2

            // when
            val result = persistentCampaignService.search("my-tenant", listOf("test", "he*lo"), "name", 0, 20)

            // then
            assertThat(result).all {
                prop(io.qalipsis.api.query.Page<Campaign>::page).isEqualTo(0)
                prop(io.qalipsis.api.query.Page<Campaign>::totalPages).isEqualTo(1)
                prop(io.qalipsis.api.query.Page<Campaign>::totalElements).isEqualTo(2)
                prop(io.qalipsis.api.query.Page<Campaign>::elements).all {
                    hasSize(2)
                    containsExactly(campaign1, campaign2)
                }
            }
            coVerifyOrder {
                campaignRepository.findAll("my-tenant", listOf(filter1, filter2), pageable)
                campaignConverter.convertToModel(refEq(campaignEntity1))
                campaignConverter.convertToModel(refEq(campaignEntity2))
            }
            confirmVerified(campaignRepository, campaignConverter)
        }

    @Test
    internal fun `should save the aborter to the campaign`() = testDispatcherProvider.run {
        val now = Instant.now()
        val campaign = CampaignEntity(
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.2,
            start = now,
            configurer = 199
        )
        coEvery { campaignRepository.findByKey("my-tenant", "my-campaign") } returns campaign
        coEvery { userRepository.findIdByUsername("my-aborter") } returns 111

        // when
        persistentCampaignService.abort("my-tenant", "my-aborter", "my-campaign")

        // then
        val capturedEntity = mutableListOf<CampaignEntity>()
        coVerifyOnce {
            campaignRepository.findByKey("my-tenant", "my-campaign")
            userRepository.findIdByUsername("my-aborter")
            campaignRepository.update(capture(capturedEntity))
        }
        confirmVerified(campaignRepository, userRepository, campaignScenarioRepository)
        assertThat(capturedEntity).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignEntity::class).isDataClassEqualTo(
                    CampaignEntity(
                        key = "my-campaign",
                        name = "This is a campaign",
                        speedFactor = 123.2,
                        start = now,
                        configurer = 199,
                        aborter = 111
                    )
                )
            }
        }
    }
}