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

package io.qalipsis.core.head.hook

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.executionprofile.CompletionMode
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.Stage
import io.qalipsis.core.executionprofile.StageExecutionProfileConfiguration
import io.qalipsis.core.head.configuration.DefaultCampaignConfiguration
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.Zone
import io.qalipsis.core.head.web.handler.BulkIllegalArgumentException
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 * @author Joël Valère
 */
@WithMockk
internal class ValidationCampaignHookTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignConstraints: DefaultCampaignConfiguration.Validation

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @InjectMockKs
    private lateinit var campaignHook: ValidationCampaignHook

    @BeforeEach
    internal fun setup() {
        every { headConfiguration.cluster.zones } returns setOf(
            Zone(key = "FR", title = "France", description = "description"),
            Zone(key = "EN", title = "England", description = "description")
        )
        every { campaignConstraints.maxMinionsCount } returns 10_000
        every { campaignConstraints.maxExecutionDuration } returns Duration.ofHours(1)
        every { campaignConstraints.maxScenariosCount } returns 4
        every { campaignConstraints.stage.minMinionsCount } returns 1
        every { campaignConstraints.stage.maxMinionsCount } returns 100
        every { campaignConstraints.stage.minResolution } returns Duration.ofMillis(500)
        every { campaignConstraints.stage.maxResolution } returns Duration.ofMinutes(5)
        every { campaignConstraints.stage.minDuration } returns Duration.ofSeconds(5)
        every { campaignConstraints.stage.maxDuration } returns Duration.ofMinutes(10)
        every { campaignConstraints.stage.minStartDuration } returns Duration.ofSeconds(5)
        every { campaignConstraints.stage.maxStartDuration } returns Duration.ofMinutes(10)
    }

    private val stagePrototype = Stage(
        minionsCount = 1,
        rampUpDurationMs = 7000,
        totalDurationMs = 5000,
        resolutionMs = 500
    )

    private val stageExecutionPrototype = StageExecutionProfileConfiguration(
        completion = CompletionMode.HARD,
        stages = listOf(stagePrototype, stagePrototype)
    )

    @Test
    internal fun `should accept a campaign with minimal information`() = testDispatcherProvider.runTest {
        // given
        val configuration = CampaignConfiguration(
            name = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf("Scenario1" to ScenarioRequest(1))
        )
        val running = RunningCampaign(
            tenant = "my-tenant",
            key = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf(
                "Scenario1" to ScenarioConfiguration(1, DefaultExecutionProfileConfiguration()),
            )
        )
        // when + then
        assertDoesNotThrow {
            campaignHook.preCreate(configuration, running)
        }
    }

    @Test
    internal fun `should accept a campaign with all fields`() = testDispatcherProvider.runTest {
        // given
        val configuration = CampaignConfiguration(
            name = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val running = RunningCampaign(
            tenant = "my-tenant",
            key = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf(
                "Scenario1" to ScenarioConfiguration(1, stageExecutionPrototype, zones = mapOf("FR" to 50, "EN" to 50)),
                "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype, zones = mapOf("FR" to 100))
            )
        )
        // when + then
        assertDoesNotThrow {
            campaignHook.preCreate(configuration, running)
        }
    }

    @Test
    internal fun `should deny a campaign when the count of minions exceed`() = testDispatcherProvider.runTest {
        // given
        val configuration = CampaignConfiguration(
            name = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf("Scenario1" to ScenarioRequest(1))
        )
        val running = RunningCampaign(
            tenant = "my-tenant",
            key = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf(
                "Scenario1" to ScenarioConfiguration(10001, DefaultExecutionProfileConfiguration()),
            )
        )
        // when
        val exception = assertThrows<BulkIllegalArgumentException> {
            campaignHook.preCreate(configuration, running)
        }
        // then
        assertThat(exception.messages.toList()).all {
            hasSize(1)
            index(0).isEqualTo("The count of minions in the campaign should not exceed 10000")
        }
    }

    @Test
    internal fun `should deny a campaign when the count of scenarios exceed`() = testDispatcherProvider.runTest {
        // given
        val configuration = CampaignConfiguration(
            name = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = (1..5).associate { "Scenario1$it" to ScenarioRequest(1) }
        )
        val running = RunningCampaign(
            tenant = "my-tenant",
            key = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf(
                "Scenario1" to ScenarioConfiguration(10, DefaultExecutionProfileConfiguration()),
            )
        )
        // when
        val exception = assertThrows<BulkIllegalArgumentException> {
            campaignHook.preCreate(configuration, running)
        }
        // then
        assertThat(exception.messages.toList()).all {
            hasSize(1)
            index(0).isEqualTo("The count of scenarios in the campaign should not exceed 4")
        }
    }

    @Test
    internal fun `should deny a campaign when the start resolution for a stage is less than minimal resolution`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    resolutionMs = 400
                                )
                            )
                        )
                    ),
                    "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype)
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The start resolution for a stage should at least be 500 milliseconds")
            }
        }

    @Test
    internal fun `should deny a campaign when the start resolution for a stage exceed maximal resolution`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    resolutionMs = 300001
                                )
                            )
                        )
                    ),
                    "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype)
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The start resolution for a stage should be less than or equal to 300000 milliseconds")
            }
        }

    @Test
    internal fun `should deny a campaign when the minions count for a stage is less than the minimal number`() =
        testDispatcherProvider.runTest {
            // given
            every { campaignConstraints.stage.minMinionsCount } returns 5
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    minionsCount = 2
                                )
                            )
                        )
                    )
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The minimum minions count for a stage should be 5")
            }
        }

    @Test
    internal fun `should deny a campaign when the minions count for a stage exceed the maximal number`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    minionsCount = 101
                                )
                            )
                        )
                    )
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The maximum minions count for a stage should be 100")
            }
        }

    @Test
    internal fun `should deny a campaign when the minimum duration for a stage is less than the minimal duration`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    totalDurationMs = 4000
                                )
                            )
                        )
                    ),
                    "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype)
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The minimum duration for a stage should be 5000 milliseconds")
            }
        }

    @Test
    internal fun `should deny a campaign when the maximum duration for a stage exceed the maximum duration`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    totalDurationMs = 600001
                                )
                            )
                        )
                    ),
                    "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype)
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The maximum duration for a stage should be 600000 milliseconds")
            }
        }

    @Test
    internal fun `should deny a campaign when the minimum ramp-up duration for a stage is less than the minimal duration`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    rampUpDurationMs = 4000
                                )
                            )
                        )
                    ),
                    "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype)
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The minimum ramp-up duration for a stage should be 5000 milliseconds")
            }
        }

    @Test
    internal fun `should deny a campaign when the maximum ramp-up duration for a stage exceed the maximum duration`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype.copy(
                            stages = listOf(
                                stagePrototype.copy(
                                    rampUpDurationMs = 600001
                                )
                            )
                        )
                    ),
                    "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype)
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The maximum ramp-up duration for a stage should be 600000 milliseconds")
            }
        }

    @Test
    internal fun `should deny a campaign when the requested zones for stage are unknown`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype, zones = mapOf("CM" to 50, "NG" to 50)
                    ),
                    "Scenario2" to ScenarioConfiguration(11, stageExecutionPrototype)
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(1)
                index(0).isEqualTo("The requested zones CM, NG are not known")
            }
        }

    @Test
    internal fun `should deny a campaign when the requested zones distribution is not 100`() =
        testDispatcherProvider.runTest {
            // given
            val configuration = CampaignConfiguration(
                name = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
            )
            val running = RunningCampaign(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(
                        1, stageExecutionPrototype, zones = mapOf("FR" to 50, "EN" to 60)
                    ),
                    "Scenario2" to ScenarioConfiguration(
                        11, stageExecutionPrototype, zones = mapOf("FR" to 20, "EN" to 60)
                    )
                )
            )
            // when
            val exception = assertThrows<BulkIllegalArgumentException> {
                campaignHook.preCreate(configuration, running)
            }
            // then
            assertThat(exception.messages.toList()).all {
                hasSize(2)
                index(0).isEqualTo("The distribution of the load across the different zones should equal to 100%")
                index(1).isEqualTo("The distribution of the load across the different zones should equal to 100%")
            }
        }

    @Test
    internal fun `should deny a campaign when fields are all incorrect`() = testDispatcherProvider.runTest {
        // given
        every { campaignConstraints.stage.minMinionsCount } returns 5
        val configuration = CampaignConfiguration(
            name = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = (1..5).associate { "Scenario1$it" to ScenarioRequest(1) }
        )
        val running = RunningCampaign(
            tenant = "my-tenant",
            key = "my-campaign",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf(
                "Scenario1" to ScenarioConfiguration(
                    10001, stageExecutionPrototype.copy(
                        stages = listOf(
                            stagePrototype.copy(
                                minionsCount = 2,
                                resolutionMs = 400,
                                totalDurationMs = 4000,
                                rampUpDurationMs = 4000
                            )
                        )
                    ), zones = mapOf("FR" to 50, "EN" to 60)
                ),
                "Scenario2" to ScenarioConfiguration(
                    11, stageExecutionPrototype.copy(
                        stages = listOf(
                            stagePrototype.copy(
                                minionsCount = 101,
                                resolutionMs = 300001,
                                totalDurationMs = 600001,
                                rampUpDurationMs = 600001
                            )
                        )
                    ), zones = mapOf("FR" to 20, "EN" to 60)
                )
            )
        )
        // when
        val exception = assertThrows<BulkIllegalArgumentException> {
            campaignHook.preCreate(configuration, running)
        }
        // then
        assertThat(exception.messages.toList()).all {
            hasSize(12)
            index(0).isEqualTo("The count of scenarios in the campaign should not exceed 4")
            index(1).isEqualTo("The count of minions in the campaign should not exceed 10000")
            index(2).isEqualTo("The distribution of the load across the different zones should equal to 100%")
            index(3).isEqualTo("The distribution of the load across the different zones should equal to 100%")
            index(4).isEqualTo("The start resolution for a stage should at least be 500 milliseconds")
            index(5).isEqualTo("The minimum minions count for a stage should be 5")
            index(6).isEqualTo("The minimum duration for a stage should be 5000 milliseconds")
            index(7).isEqualTo("The minimum ramp-up duration for a stage should be 5000 milliseconds")
            index(8).isEqualTo("The start resolution for a stage should be less than or equal to 300000 milliseconds")
            index(9).isEqualTo("The maximum minions count for a stage should be 100")
            index(10).isEqualTo("The maximum duration for a stage should be 600000 milliseconds")
            index(11).isEqualTo("The maximum ramp-up duration for a stage should be 600000 milliseconds")
        }
    }
}