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

package io.qalipsis.core.head.model.converter

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.configuration.AcceleratingExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.RegularExternalExecutionProfileConfiguration
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class CampaignConfigurationConverterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    private lateinit var converter: CampaignConfigurationConverterImpl

    @BeforeAll
    internal fun setUp() {
        converter = CampaignConfigurationConverterImpl(idGenerator = idGenerator)
    }

    @Test
    internal fun `should convert the minimal request`() = testDispatcherProvider.runTest {
        // given
        every { idGenerator.long() } returns "my-campaign"
        val request = CampaignConfiguration(
            name = "Anything",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        // when
        val result = converter.convertConfiguration("my-tenant", request)

        // then
        assertThat(result).isDataClassEqualTo(
            RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(1, DefaultExecutionProfileConfiguration()),
                    "Scenario2" to ScenarioConfiguration(11, DefaultExecutionProfileConfiguration())
                )
            )
        )
    }

    @Test
    internal fun `should convert the complete request`() = testDispatcherProvider.runTest {
        // given
        every { idGenerator.long() } returns "my-campaign"
        val request = CampaignConfiguration(
            name = "Anything",
            speedFactor = 1.43,
            startOffsetMs = 123,
            timeout = Duration.ofSeconds(2345),
            hardTimeout = true,
            scenarios = mapOf(
                "Scenario1" to ScenarioRequest(
                    1,
                    AcceleratingExternalExecutionProfileConfiguration(
                        startPeriodMs = 500,
                        accelerator = 2.0,
                        minPeriodMs = 100,
                        minionsCountProLaunch = 1
                    )
                ),
                "Scenario2" to ScenarioRequest(
                    11,
                    RegularExternalExecutionProfileConfiguration(
                        periodInMs = 1000,
                        minionsCountProLaunch = 2
                    )
                )
            )
        )

        // when
        val result = converter.convertConfiguration("my-tenant", request)

        // then
        assertThat(result).isDataClassEqualTo(
            RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1,
                        AcceleratingExecutionProfileConfiguration(
                            startPeriodMs = 500,
                            accelerator = 2.0,
                            minPeriodMs = 100,
                            minionsCountProLaunch = 1
                        )
                    ),
                    "Scenario2" to ScenarioConfiguration(
                        11,
                        RegularExecutionProfileConfiguration(
                            periodInMs = 1000,
                            minionsCountProLaunch = 2
                        )
                    )
                )
            )
        )
    }

    @Test
    internal fun `should successfully convert the minimal request with defined zones`() =
        testDispatcherProvider.runTest {
            // given
            every { idGenerator.long() } returns "my-campaign"
            val request = CampaignConfiguration(
                name = "Anything",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioRequest(1, zones = mapOf("FR" to 100)),
                    "Scenario2" to ScenarioRequest(11, zones = mapOf("EN" to 10, "FR" to 90))
                )
            )

            // when
            val result = converter.convertConfiguration("my-tenant", request)

            // then
            assertThat(result).isDataClassEqualTo(
                RunningCampaign(
                    tenant = "my-tenant",
                    key = "my-campaign",
                    speedFactor = 1.43,
                    startOffsetMs = 123,
                    scenarios = mapOf(
                        "Scenario1" to ScenarioConfiguration(
                            1,
                            DefaultExecutionProfileConfiguration(),
                            zones = mapOf("FR" to 100)
                        ),
                        "Scenario2" to ScenarioConfiguration(
                            11,
                            DefaultExecutionProfileConfiguration(),
                            zones = mapOf("EN" to 10, "FR" to 90)
                        )
                    )
                )
            )
        }
}