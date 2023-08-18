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
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.campaign.scheduler.CampaignScheduler
import io.qalipsis.core.head.campaign.scheduler.ScheduledCampaignsRegistry
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.FactoryRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

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
    private lateinit var campaignConverter: CampaignConverter

    @RelaxedMockK
    private lateinit var factoryRepository: FactoryRepository

    @RelaxedMockK
    private lateinit var campaignPreparator: CampaignPreparator

    @RelaxedMockK
    private lateinit var campaignScheduler: CampaignScheduler

    @RelaxedMockK
    private lateinit var scheduledCampaignsRegistry: ScheduledCampaignsRegistry

    private lateinit var persistentCampaignService: PersistentCampaignService

    @BeforeAll
    internal fun setUp() {
        persistentCampaignService = spyk(
            PersistentCampaignService(
                campaignRepository = campaignRepository,
                userRepository = userRepository,
                campaignScenarioRepository = campaignScenarioRepository,
                campaignConverter = campaignConverter,
                factoryRepository = factoryRepository,
                campaignPreparator = campaignPreparator,
                scheduledCampaignsRegistry = scheduledCampaignsRegistry
            ),
            recordPrivateCalls = true
        )
    }

    @Test
    internal fun `should create the new campaign`() = testDispatcherProvider.run {
        // given
        val campaign = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            )
        )
        val runningCampaign = relaxedMockk<RunningCampaign> {
            every { key } returns "my-campaign"
            every { scenarios } returns mapOf(
                "scenario-1" to relaxedMockk { every { minionsCount } returns 6272 },
                "scenario-2" to relaxedMockk { every { minionsCount } returns 12321 }
            )
        }
        coEvery {
            campaignPreparator.convertAndSaveCampaign(
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(campaign),
                refEq(false)
            )
        } returns runningCampaign

        // when
        val result = persistentCampaignService.create("my-tenant", "my-user", campaign)

        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            campaignPreparator.convertAndSaveCampaign(
                refEq("my-tenant"),
                refEq("my-user"),
                refEq(campaign),
                refEq(false)
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }

    @Test
    internal fun `should prepare the campaign`() = testDispatcherProvider.run {
        // given
        coJustRun { campaignRepository.prepare(any(), any()) }

        // when
        persistentCampaignService.prepare("my-tenant", "my-campaign")

        // then
        coVerifyOnce {
            campaignRepository.prepare("my-tenant", "my-campaign")
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }

    @Test
    internal fun `should start the campaign`() = testDispatcherProvider.run {
        // given
        val start = Instant.now()
        val timeout = Instant.now().plusSeconds(243)
        coJustRun { campaignRepository.start("my-tenant", "my-campaign", start, timeout, null) }

        // when
        persistentCampaignService.start("my-tenant", "my-campaign", start, timeout, null)

        // then
        coVerifyOnce {
            campaignRepository.start("my-tenant", "my-campaign", start, timeout, null)
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }

    @Test
    internal fun `should close the running campaign`() = testDispatcherProvider.run {
        // given
        val campaignEntity = relaxedMockk<CampaignEntity>()
        val convertedCampaign = relaxedMockk<Campaign>()
        coEvery { campaignConverter.convertToModel(refEq(campaignEntity)) } returns convertedCampaign
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaignEntity

        // when
        val result =
            persistentCampaignService.close("my-tenant", "my-campaign", ExecutionStatus.FAILED, "This is the failure")

        // then
        assertThat(result).isSameAs(convertedCampaign)
        coVerifyOnce {
            campaignRepository.complete("my-tenant", "my-campaign", ExecutionStatus.FAILED, "This is the failure")
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            campaignConverter.convertToModel(refEq(campaignEntity))
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }

    @Test
    internal fun `should returns the searched campaigns from the repository with default sorting`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("start")))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), pageable, 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
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

            confirmVerified(
                userRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConverter,
                factoryRepository,
                campaignPreparator
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting asc`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name", true)))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
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

            confirmVerified(
                userRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConverter,
                factoryRepository,
                campaignPreparator
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting desc`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.desc("name", true)))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
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

            confirmVerified(
                userRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConverter,
                factoryRepository,
                campaignPreparator
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name", true)))
            val page = Page.of(listOf(campaignEntity1, campaignEntity2), Pageable.from(0, 20), 2)

            val campaign1 = relaxedMockk<Campaign>()
            val campaign2 = relaxedMockk<Campaign>()
            coEvery { campaignRepository.findAll("my-tenant", pageable) } returns page
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

            confirmVerified(
                userRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConverter,
                factoryRepository,
                campaignPreparator
            )
        }

    @Test
    internal fun `should returns the searched campaigns from the repository with sorting and filtering`() =
        testDispatcherProvider.run {
            // given
            val campaignEntity1 = relaxedMockk<CampaignEntity>()
            val campaignEntity2 = relaxedMockk<CampaignEntity>()
            val filter1 = "%test%"
            val filter2 = "%he%lo%"
            val pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("name", true)))
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

            confirmVerified(
                userRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConverter,
                factoryRepository,
                campaignPreparator
            )
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
            result = ExecutionStatus.ABORTED,
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
        assertThat(capturedEntity).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignEntity::class).all {
                    prop(CampaignEntity::key).isEqualTo("my-campaign")
                    prop(CampaignEntity::name).isEqualTo("This is a campaign")
                    prop(CampaignEntity::scheduledMinions).isEqualTo(345)
                    prop(CampaignEntity::speedFactor).isEqualTo(123.2)
                    prop(CampaignEntity::configurer).isEqualTo(199)
                    prop(CampaignEntity::aborter).isEqualTo(111)
                    prop(CampaignEntity::result).isEqualTo(ExecutionStatus.ABORTED)
                }
            }
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }

    @Test
    internal fun `should cancel future jobs when the campaign status is scheduled`() = testDispatcherProvider.run {
        val now = Instant.now()
        val campaign = CampaignEntity(
            key = "my-campaign",
            name = "This is a campaign",
            speedFactor = 123.2,
            scheduledMinions = 345,
            start = now,
            configurer = 199,
            result = ExecutionStatus.SCHEDULED
        )
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns campaign
        coEvery { userRepository.findIdByUsername("my-aborter") } returns 111
        coEvery { campaignRepository.update(any()) } returnsArgument 0
        coEvery { scheduledCampaignsRegistry.cancelSchedule(any()) } returnsArgument 0

        // when
        persistentCampaignService.abort("my-tenant", "my-aborter", "my-campaign")

        // then
        val capturedEntity = mutableListOf<CampaignEntity>()
        coVerifyOnce {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            userRepository.findIdByUsername("my-aborter")
            campaignRepository.update(capture(capturedEntity))
            scheduledCampaignsRegistry.cancelSchedule("my-campaign")
        }
        assertThat(capturedEntity).all {
            hasSize(1)
            any {
                it.isInstanceOf(CampaignEntity::class).all {
                    prop(CampaignEntity::key).isEqualTo("my-campaign")
                    prop(CampaignEntity::name).isEqualTo("This is a campaign")
                    prop(CampaignEntity::scheduledMinions).isEqualTo(345)
                    prop(CampaignEntity::speedFactor).isEqualTo(123.2)
                    prop(CampaignEntity::configurer).isEqualTo(199)
                    prop(CampaignEntity::aborter).isEqualTo(111)
                    prop(CampaignEntity::result).isEqualTo(ExecutionStatus.ABORTED)
                }
            }
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator,
            campaignScheduler
        )
    }

    @Test
    internal fun `should enrich the campaign without failure message`() = testDispatcherProvider.run {
        // given
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
        coEvery { campaignRepository.update(any()) } returnsArgument 0
        coEvery {
            factoryRepository.findByNodeIdIn(
                "my-tenant",
                setOf("factory-1", "factory-2", "factory-3")
            )
        } returns listOf(
            mockk { every { zone } returns "zone-1" },
            mockk { every { zone } returns null },
            mockk { every { zone } returns "zone-3" }
        )

        // when
        persistentCampaignService.enrich(mockk {
            every { tenant } returns "my-tenant"
            every { key } returns "my-campaign"
            every { message } returns " "
            every { factories.keys } returns mutableSetOf("factory-1", "factory-2", "factory-3")
        })

        // then
        coVerifyOnce {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            factoryRepository.findByNodeIdIn("my-tenant", setOf("factory-1", "factory-2", "factory-3"))
            campaignRepository.update(
                eq(
                    CampaignEntity(
                        key = "my-campaign",
                        name = "This is a campaign",
                        speedFactor = 123.2,
                        scheduledMinions = 345,
                        start = now,
                        configurer = 199,
                        failureReason = null,
                        zones = setOf("zone-1", "zone-3")
                    ).copy(
                        creation = campaign.creation,
                        version = campaign.version
                    )
                )
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }


    @Test
    internal fun `should enrich the campaign with failure message`() = testDispatcherProvider.run {
        // given
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
        coEvery { campaignRepository.update(any()) } returnsArgument 0
        coEvery {
            factoryRepository.findByNodeIdIn(
                "my-tenant",
                setOf("factory-1", "factory-2", "factory-3")
            )
        } returns listOf(
            mockk { every { zone } returns "zone-1" },
            mockk { every { zone } returns null },
            mockk { every { zone } returns "zone-3" }
        )

        // when
        persistentCampaignService.enrich(mockk {
            every { tenant } returns "my-tenant"
            every { key } returns "my-campaign"
            every { message } returns "The failure"
            every { factories.keys } returns mutableSetOf("factory-1", "factory-2", "factory-3")
        })

        // then
        coVerifyOnce {
            campaignRepository.findByTenantAndKey("my-tenant", "my-campaign")
            factoryRepository.findByNodeIdIn("my-tenant", setOf("factory-1", "factory-2", "factory-3"))
            campaignRepository.update(
                eq(
                    CampaignEntity(
                        key = "my-campaign",
                        name = "This is a campaign",
                        speedFactor = 123.2,
                        scheduledMinions = 345,
                        start = now,
                        configurer = 199,
                        failureReason = "The failure",
                        zones = setOf("zone-1", "zone-3")
                    ).copy(
                        creation = campaign.creation,
                        version = campaign.version
                    )
                )
            )
        }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }

    @Test
    internal fun `should retrieve the configuration when it exists`() = testDispatcherProvider.run {
        // given
        val campaignConfiguration = mockk<CampaignConfiguration>()
        coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns mockk {
            every { configuration } returns campaignConfiguration
        }

        // when
        val retrievedConfiguration = persistentCampaignService.retrieveConfiguration("my-tenant", "my-campaign")

        // then
        assertThat(retrievedConfiguration).isSameAs(campaignConfiguration)
        coVerifyOnce { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") }

        confirmVerified(
            userRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConverter,
            factoryRepository,
            campaignPreparator
        )
    }

    @Test
    internal fun `should generate a failure when retrieving the configuration of a non-existing campaign`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns null

            // when
            assertThrows<IllegalArgumentException> {
                persistentCampaignService.retrieveConfiguration("my-tenant", "my-campaign")
            }

            // then
            coVerifyOnce { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") }

            confirmVerified(
                userRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConverter,
                factoryRepository,
                campaignPreparator
            )
        }

    @Test
    internal fun `should generate a failure when retrieving the missing configuration of an existing campaign`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") } returns mockk {
                every { configuration } returns null
            }

            // when
            assertThrows<IllegalArgumentException> {
                persistentCampaignService.retrieveConfiguration("my-tenant", "my-campaign")
            }

            // then
            coVerifyOnce { campaignRepository.findByTenantAndKey("my-tenant", "my-campaign") }

            confirmVerified(
                userRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConverter,
                factoryRepository,
                campaignPreparator
            )
        }
}