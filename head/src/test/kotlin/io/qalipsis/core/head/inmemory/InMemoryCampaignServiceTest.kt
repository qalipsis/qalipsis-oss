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

package io.qalipsis.core.head.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.head.hook.CampaignHook
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import io.qalipsis.core.head.web.handler.BulkIllegalArgumentException
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import java.time.Duration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class InMemoryCampaignServiceTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    @RelaxedMockK
    private lateinit var hook1: CampaignHook

    @RelaxedMockK
    private lateinit var hook2: CampaignHook

    private lateinit var inMemoryCampaignService: InMemoryCampaignService

    @BeforeAll
    internal fun setUp() {
        inMemoryCampaignService = spyk(
            InMemoryCampaignService(
                campaignConfigurationConverter,
                hooks = listOf(hook1, hook2)
            )
        )
    }

    @Test
    internal fun `should create a campaign`() = testDispatcherProvider.run {
        // given
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            timeout = Duration.ofSeconds(715),
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
                refEq(configuration)
            )
        } returns runningCampaign

        // when
        val result = inMemoryCampaignService.create("my-tenant", "my-user", configuration)


        // then
        assertThat(result).isSameAs(runningCampaign)
        coVerifyOrder {
            campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(configuration))
            hook1.preCreate(refEq(configuration), refEq(runningCampaign))
            hook2.preCreate(refEq(configuration), refEq(runningCampaign))
        }
        confirmVerified(
            campaignConfigurationConverter,
            hook1,
            hook2
        )
    }

    @Test
    internal fun `should correctly handle the first hook exceptions when scheduling a campaign`() =
        testDispatcherProvider.run {
            // given
            val configuration = CampaignConfiguration(
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
                    refEq(configuration)
                )
            } returns runningCampaign
            coEvery {
                hook1.preCreate(refEq(configuration), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                inMemoryCampaignService.create(
                    "my-tenant",
                    "my-user",
                    configuration,
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(configuration))
                hook1.preCreate(refEq(configuration), refEq(runningCampaign))
            }

            confirmVerified(
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should stop immediately if the first hook raises exceptions when scheduling a campaign`() =
        testDispatcherProvider.run {
            // given
            val configuration = CampaignConfiguration(
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
                    refEq(configuration)
                )
            } returns runningCampaign
            coEvery {
                hook1.preCreate(refEq(configuration), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors one", "Constraints errors two"))
            coEvery {
                hook2.preCreate(refEq(configuration), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                inMemoryCampaignService.create(
                    "my-tenant",
                    "my-user",
                    configuration,
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors one")
                index(1).isEqualTo("Constraints errors two")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(configuration))
                hook1.preCreate(refEq(configuration), refEq(runningCampaign))
            }

            confirmVerified(
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should correctly handle any hook exception in provided order when scheduling a campaign`() =
        testDispatcherProvider.run {
            // given
            val configuration = CampaignConfiguration(
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
                    refEq(configuration)
                )
            } returns runningCampaign
            coEvery {
                hook2.preCreate(refEq(configuration), refEq(runningCampaign))
            } throws BulkIllegalArgumentException(listOf("Constraints errors three", "Constraints errors four"))

            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                inMemoryCampaignService.create(
                    "my-tenant",
                    "my-user",
                    configuration
                )
            }

            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("Constraints errors three")
                index(1).isEqualTo("Constraints errors four")
            }
            coVerifyOrder {
                campaignConfigurationConverter.convertConfiguration("my-tenant", refEq(configuration))
                hook1.preCreate(refEq(configuration), refEq(runningCampaign))
                hook2.preCreate(refEq(configuration), refEq(runningCampaign))
            }

            confirmVerified(
                campaignConfigurationConverter,
                hook1,
                hook2
            )
        }

    @Test
    internal fun `should not schedule a campaign`() = testDispatcherProvider.run {
        // given
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            )
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            inMemoryCampaignService.schedule("my-tenant", "my-user", configuration)
        }

        // then
        assertThat(exception.message)
            .isEqualTo("In-memory campaign manager does not support scheduling")

        confirmVerified(
            campaignConfigurationConverter,
            hook1,
            hook2
        )
    }

}