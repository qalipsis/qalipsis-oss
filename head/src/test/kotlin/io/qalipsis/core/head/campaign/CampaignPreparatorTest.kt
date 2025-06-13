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
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.report.ExecutionStatus.QUEUED
import io.qalipsis.api.report.ExecutionStatus.SCHEDULED
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import io.qalipsis.core.head.web.handler.BulkIllegalArgumentException
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

@WithMockk
internal class CampaignPreparatorTest {

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
    private lateinit var hook1: CampaignHook

    @RelaxedMockK
    private lateinit var hook2: CampaignHook

    private lateinit var campaignPreparator: CampaignPreparator

    @BeforeAll
    internal fun setUp() {
        campaignPreparator = spyk(
            CampaignPreparator(
                userRepository = userRepository,
                tenantRepository = tenantRepository,
                campaignRepository = campaignRepository,
                campaignScenarioRepository = campaignScenarioRepository,
                campaignConfigurationConverter = campaignConfigurationConverter,
                hooks = listOf(hook1, hook2)
            ),
            recordPrivateCalls = true
        )
    }

    @Test
    internal fun `should save a campaign during creation without timeout`() = testDispatcherProvider.run {
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
        val savedEntity = relaxedMockk<CampaignEntity> {
            every { id } returns 8126
        }
        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
        coEvery {
            campaignConfigurationConverter.convertConfiguration(
                "my-tenant",
                refEq(campaign)
            )
        } returns runningCampaign
        coEvery { campaignRepository.save(any()) } returns savedEntity
        coEvery { userRepository.findIdByUsername("my-user") } returns 199

        // when
        val result = campaignPreparator.convertAndSaveCampaign(
            "my-tenant",
            "my-user",
            campaign,
            false
        )

        // then
        assertThat(result).isSameInstanceAs(runningCampaign)
        coVerifyOrder {
            campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
            hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            tenantRepository.findIdByReference("my-tenant")
            userRepository.findIdByUsername("my-user")
            campaignRepository.save(withArg {
                assertThat(it).all {
                    prop(CampaignEntity::key).isEqualTo("my-campaign")
                    prop(CampaignEntity::name).isEqualTo("This is a campaign")
                    prop(CampaignEntity::speedFactor).isEqualTo(123.2)
                    prop(CampaignEntity::scheduledMinions).isEqualTo(18593)
                    prop(CampaignEntity::hardTimeout).isNull()
                    prop(CampaignEntity::softTimeout).isNull()
                    prop(CampaignEntity::configurer).isEqualTo(199L)
                    prop(CampaignEntity::tenantId).isEqualTo(8165L)
                    prop(CampaignEntity::configuration).isSameInstanceAs(campaign)
                    prop(CampaignEntity::result).isEqualTo(QUEUED)
                }
            })
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(8126, "scenario-2", minionsCount = 12321)
                )
            )
        }

        confirmVerified(
            userRepository,
            tenantRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should correctly handle the first hook exceptions when saving a campaign`() =
        testDispatcherProvider.run {
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
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignPreparator.convertAndSaveCampaign(
                    "my-tenant",
                    "my-user",
                    campaign,
                    false
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                tenantRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should stop immediately if the first hook raises exceptions when saving a campaign`() =
        testDispatcherProvider.run {
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
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))
            coEvery {
                hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignPreparator.convertAndSaveCampaign(
                    "my-tenant",
                    "my-user",
                    campaign,
                    false
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                tenantRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should correctly handle any hook exception in provided order when saving a campaign`() =
        testDispatcherProvider.run {
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
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignPreparator.convertAndSaveCampaign(
                    "my-tenant",
                    "my-user",
                    campaign,
                    false
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors three")
                index(1).isEqualTo("Constraints errors four")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preCreate(refEq(campaign), refEq(runningCampaign))
                hook2.preCreate(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                tenantRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should save a campaign during scheduling with timeout`() = testDispatcherProvider.run {
        // given
        val scheduleAt = Instant.now().plusSeconds(60)
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            timeout = Duration.ofSeconds(715),
            hardTimeout = false,
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
        val savedEntity = relaxedMockk<CampaignEntity> {
            every { id } returns 8126
        }

        coEvery { tenantRepository.findIdByReference("my-tenant") } returns 8165L
        coEvery {
            campaignConfigurationConverter.convertConfiguration(
                "my-tenant",
                refEq(configuration)
            )
        } returns runningCampaign
        coEvery { campaignRepository.save(any()) } returns savedEntity
        coEvery { userRepository.findIdByUsername("my-user") } returns 199
        // when
        val result = campaignPreparator.convertAndSaveCampaign(
            "my-tenant",
            "my-user",
            configuration,
            true
        )

        // then
        assertThat(result).isSameInstanceAs(runningCampaign)
        coVerifyOrder {
            campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(configuration))
            hook1.preSchedule(refEq(configuration), refEq(runningCampaign))
            hook2.preSchedule(refEq(configuration), refEq(runningCampaign))
            tenantRepository.findIdByReference("my-tenant")
            userRepository.findIdByUsername("my-user")
            campaignRepository.save(withArg {
                assertThat(it).all {
                    prop(CampaignEntity::key).isEqualTo("my-campaign")
                    prop(CampaignEntity::name).isEqualTo("This is a campaign")
                    prop(CampaignEntity::speedFactor).isEqualTo(123.2)
                    prop(CampaignEntity::scheduledMinions).isEqualTo(18593)
                    prop(CampaignEntity::hardTimeout).isNull()
                    prop(CampaignEntity::softTimeout).isNull()
                    prop(CampaignEntity::configurer).isEqualTo(199L)
                    prop(CampaignEntity::tenantId).isEqualTo(8165L)
                    prop(CampaignEntity::configuration).isSameInstanceAs(configuration)
                    prop(CampaignEntity::result).isEqualTo(SCHEDULED)
                }
            })
            campaignScenarioRepository.saveAll(
                listOf(
                    CampaignScenarioEntity(8126, "scenario-1", minionsCount = 6272),
                    CampaignScenarioEntity(8126, "scenario-2", minionsCount = 12321)
                )
            )
        }

        confirmVerified(
            userRepository,
            tenantRepository,
            campaignRepository,
            campaignScenarioRepository,
            campaignConfigurationConverter,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should correctly handle the first hook exceptions when scheduling a campaign`() =
        testDispatcherProvider.run {
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
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignPreparator.convertAndSaveCampaign(
                    "my-tenant",
                    "my-user",
                    campaign,
                    true
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                tenantRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should stop immediately if the first hook raises exceptions when scheduling a campaign`() =
        testDispatcherProvider.run {
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
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))
            coEvery {
                hook2.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignPreparator.convertAndSaveCampaign(
                    "my-tenant",
                    "my-user",
                    campaign,
                    true
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                tenantRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should correctly handle any hook exception in provided order when scheduling a campaign`() =
        testDispatcherProvider.run {
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
                campaignConfigurationConverter.convertConfiguration(
                    "my-tenant",
                    refEq(campaign)
                )
            } returns runningCampaign
            coEvery {
                hook2.preSchedule(refEq(campaign), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignPreparator.convertAndSaveCampaign(
                    "my-tenant",
                    "my-user",
                    campaign,
                    true
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors three")
                index(1).isEqualTo("Constraints errors four")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(campaign))
                hook1.preSchedule(refEq(campaign), refEq(runningCampaign))
                hook2.preSchedule(refEq(campaign), refEq(runningCampaign))
            }

            confirmVerified(
                userRepository,
                tenantRepository,
                campaignRepository,
                campaignScenarioRepository,
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

}