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

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.executionprofile.CompletionMode.GRACEFUL
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ImmediateExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.PercentageStage
import io.qalipsis.core.executionprofile.PercentageStageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.ProgressiveVolumeExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.TimeFrameExecutionProfileConfiguration
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.configuration.AcceleratingExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.ImmediatelyExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.PercentageStageExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.ProgressiveVolumeExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.RegularExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.StageExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.TimeFrameExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.converter.catadioptre.defineExecutionProfileConfiguration
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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


    @Test
    @Timeout(3)
    internal fun `should convert execution profile configuration to execution profile`() =
        testDispatcherProvider.runTest {

            // when
            var convertedExecutionProfile = converter.defineExecutionProfileConfiguration(
                mockk {
                    every { minionsCount } returns 1815
                    every { executionProfile } returns RegularExternalExecutionProfileConfiguration(
                        periodInMs = 1000,
                        minionsCountProLaunch = 10
                    )
                }
            )

            // then
            assertThat(convertedExecutionProfile).isDataClassEqualTo(
                RegularExecutionProfileConfiguration(
                    periodInMs = 1000,
                    minionsCountProLaunch = 10
                ) to 1815
            )

            // when
            convertedExecutionProfile = converter.defineExecutionProfileConfiguration(
                mockk {
                    every { minionsCount } returns 28712342
                    every { executionProfile } returns ImmediatelyExternalExecutionProfileConfiguration()
                }
            )

            // then
            assertThat(convertedExecutionProfile).all {
                prop(Pair<*, *>::first).isNotNull().isInstanceOf<ImmediateExecutionProfileConfiguration>()
                prop(Pair<*, *>::second).isEqualTo(28712342)
            }

            // when
            convertedExecutionProfile = converter.defineExecutionProfileConfiguration(
                mockk {
                    every { minionsCount } returns 43224354
                    every { executionProfile } returns AcceleratingExternalExecutionProfileConfiguration(
                        startPeriodMs = 764,
                        accelerator = 123.5,
                        minPeriodMs = 234,
                        minionsCountProLaunch = 2365
                    )
                }
            )

            // then
            assertThat(convertedExecutionProfile).isDataClassEqualTo(
                AcceleratingExecutionProfileConfiguration(
                    startPeriodMs = 764,
                    accelerator = 123.5,
                    minPeriodMs = 234,
                    minionsCountProLaunch = 2365
                ) to 43224354
            )

            // when
            convertedExecutionProfile = converter.defineExecutionProfileConfiguration(
                mockk {
                    every { minionsCount } returns 8564
                    every { executionProfile } returns ProgressiveVolumeExternalExecutionProfileConfiguration(
                        periodMs = 764,
                        minionsCountProLaunchAtStart = 123,
                        multiplier = 234.5,
                        maxMinionsCountProLaunch = 2365
                    )
                }
            )

            // then
            assertThat(convertedExecutionProfile).isDataClassEqualTo(
                ProgressiveVolumeExecutionProfileConfiguration(
                    periodMs = 764,
                    minionsCountProLaunchAtStart = 123,
                    multiplier = 234.5,
                    maxMinionsCountProLaunch = 2365
                ) to 8564
            )

            // when
            convertedExecutionProfile = converter.defineExecutionProfileConfiguration(
                mockk {
                    every { minionsCount } returns 34234
                    every { executionProfile } returns TimeFrameExternalExecutionProfileConfiguration(
                        periodInMs = 764,
                        timeFrameInMs = 564
                    )
                }
            )

            // then
            assertThat(convertedExecutionProfile).isDataClassEqualTo(
                TimeFrameExecutionProfileConfiguration(periodInMs = 764, timeFrameInMs = 564) to 34234,
            )

            // when
            convertedExecutionProfile = converter.defineExecutionProfileConfiguration(
                mockk {
                    every { minionsCount } returns 3425
                    every { executionProfile } returns StageExternalExecutionProfileConfiguration(
                        listOf(
                            Stage(12, 234, 75464, 12),
                            Stage(75, 4433, 46456, 343)
                        ),
                        GRACEFUL
                    )
                }
            )

            // then
            assertThat(convertedExecutionProfile).isDataClassEqualTo(
                StageExecutionProfileConfiguration(
                    GRACEFUL,
                    listOf(
                        Stage(
                            minionsCount = 12,
                            rampUpDurationMs = 234,
                            totalDurationMs = 75464,
                            resolutionMs = 12
                        ),
                        Stage(
                            minionsCount = 75,
                            rampUpDurationMs = 4433,
                            totalDurationMs = 46456,
                            resolutionMs = 343
                        )
                    )
                ) to 87
            )

            // when
            convertedExecutionProfile = converter.defineExecutionProfileConfiguration(
                mockk {
                    every { minionsCount } returns 764534
                    every { executionProfile } returns PercentageStageExternalExecutionProfileConfiguration(
                        listOf(
                            PercentageStage(12.0, 234, 75464, 12),
                            PercentageStage(75.3, 4433, 46456, 343)
                        ),
                        GRACEFUL,
                    )
                }

            )

            // then
            assertThat(convertedExecutionProfile).isDataClassEqualTo(
                PercentageStageExecutionProfileConfiguration(
                    GRACEFUL,
                    listOf(
                        PercentageStage(
                            minionsPercentage = 12.0,
                            rampUpDurationMs = 234,
                            totalDurationMs = 75464,
                            resolutionMs = 12
                        ),
                        PercentageStage(
                            minionsPercentage = 75.3,
                            rampUpDurationMs = 4433,
                            totalDurationMs = 46456,
                            resolutionMs = 343
                        )
                    )
                ) to 764534
            )
        }
}