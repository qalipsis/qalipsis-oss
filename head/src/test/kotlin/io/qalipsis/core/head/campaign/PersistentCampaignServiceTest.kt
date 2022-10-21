/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Duration
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
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    @RelaxedMockK
    private lateinit var campaignConverter: CampaignConverter

    @InjectMockKs
    private lateinit var persistentCampaignService: PersistentCampaignService

    @Test
    internal fun `should create the new campaign without timeout`() = testDispatcherProvider.run {
        // given
        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
        val campaign = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(6272),
                "scenario-2" to ScenarioRequest(12321)
            )
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
        }
        coEvery {
            campaignConfigurationConverter.convertConfiguration(
                "my-tenant",
                refEq(campaign)
            )
        } returns runningCampaign
        val savedEntity = relaxedMockk<CampaignEntity> {
            every { id } returns 8126
        }
        coEvery { campaignRepository.save(any()) } returns savedEntity
        coEvery { userRepository.findIdByUsername("my-user") } returns 199

        // when
        val result = persistentCampaignService.create("my-tenant", "my-user", campaign)

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
            userRepository.findIdByUsername("my-user")
            campaignRepository.save(
                CampaignEntity(
                    key = "my-campaign",
                    name = "This is a campaign",
                    speedFactor = 123.2,
                    scheduledMinions = 18593,
                    configurer = 199,
                    tenantId = 8165L,
                    configuration = campaign
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(8126, "scenario-2", minionsCount = 12321)
                )
            )
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository)
    }


    @Test
    internal fun `should create the new campaign with timeout`() = testDispatcherProvider.run {
        // given
        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
        val campaign = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            timeout = Duration.ofSeconds(715),
            hardTimeout = true,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(
                    6272
                ),
                "scenario-2" to ScenarioRequest(
                    12321
                )
            )
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
        }
        coEvery {
            campaignConfigurationConverter.convertConfiguration(
                "my-tenant",
                refEq(campaign)
            )
        } returns runningCampaign
        val savedEntity = relaxedMockk<CampaignEntity> {
            every { id } returns 8126
        }
        coEvery { campaignRepository.save(any()) } returns savedEntity
        coEvery { userRepository.findIdByUsername("my-user") } returns 199

        // when
        val result = persistentCampaignService.create("my-tenant", "my-user", campaign)

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
            userRepository.findIdByUsername("my-user")
            campaignRepository.save(
                CampaignEntity(
                    key = "my-campaign",
                    name = "This is a campaign",
                    speedFactor = 123.2,
                    scheduledMinions = 18593,
                    hardTimeout = true,
                    configurer = 199,
                    tenantId = 8165L,
                    configuration = campaign
                )
            )
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(8126, "scenario-2", minionsCount = 12321)
                )
            )
        }
        confirmVerified(userRepository, campaignRepository, campaignScenarioRepository)
    }

    @Test
    internal fun `should close the running campaign`() = testDispatcherProvider.run {
        // given
        val campaignEntity = relaxedMockk<CampaignEntity>()
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaignEntity
        val convertedCampaign = relaxedMockk<Campaign>()
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns convertedCampaign

        // when
        val result = persistentCampaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED)

        // then
        assertThat(result).isSameAs(convertedCampaign)
        coVerifyOnce {
            campaignRepository.complete("my-tenant", "my-campaign", ExecutionStatus.FAILED)
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
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
            confirmVerified(campaignRepository, campaignConfigurationConverter)
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
            confirmVerified(campaignRepository, campaignConfigurationConverter)
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
            confirmVerified(campaignRepository, campaignConfigurationConverter)
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
            confirmVerified(campaignRepository, campaignConfigurationConverter)
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
            confirmVerified(campaignRepository, campaignConfigurationConverter)
        }

    @Test
    internal fun `should save the aborter to the campaign`() = testDispatcherProvider.run {
        val now = Instant.now()
        val campaign = CampaignEntity(
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.2,
            scheduledMinions = 345,
            start = now,
            configurer = 199
        )
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaign
        coEvery { userRepository.findIdByUsername("my-aborter") } returns 111
        coEvery { campaignRepository.update(any()) } returnsArgument 0

        // when
        persistentCampaignService.abort("my-tenant", "my-aborter", "my-campaign")

        // then
        val capturedEntity = mutableListOf<CampaignEntity>()
        coVerifyOnce {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
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
                        scheduledMinions = 345,
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