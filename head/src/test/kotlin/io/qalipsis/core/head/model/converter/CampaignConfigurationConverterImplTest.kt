package io.qalipsis.core.head.model.converter

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.executionprofile.AcceleratingExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.DefaultExecutionProfileConfiguration
import io.qalipsis.core.executionprofile.RegularExecutionProfileConfiguration
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.Zone
import io.qalipsis.core.head.model.configuration.AcceleratingExternalExecutionProfileConfiguration
import io.qalipsis.core.head.model.configuration.RegularExternalExecutionProfileConfiguration
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class CampaignConfigurationConverterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var converter: CampaignConfigurationConverterImpl


    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @BeforeEach
    internal fun setup() {
        every { headConfiguration.zones } returns setOf(Zone(key = "FR", title = "France", description = "description"), Zone(key = "EN", title = "England", description = "description"))
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
                hardTimeout = false,
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
                hardTimeout = true,
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
    internal fun `should not convert the minimal request with some zones are unknown`() =
        testDispatcherProvider.runTest {
            // given
            every { idGenerator.long() } returns "my-campaign"
            val request = CampaignConfiguration(
                name = "Anything",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario2" to ScenarioRequest(11, zones = mapOf("CM" to 50, "NG" to 50))
                )
            )

            assertThrows<IllegalArgumentException>("Some requested zones do not exist: CM, NG") {
                converter.convertConfiguration("my-tenant", request)
            }
        }

    @Test
    internal fun `should not convert the minimal request when the distribution is not 100%`() =
        testDispatcherProvider.runTest {
            // given
            every { idGenerator.long() } returns "my-campaign"
            val request = CampaignConfiguration(
                name = "Anything",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf(
                    "Scenario1" to ScenarioRequest(
                        1,
                        zones = mapOf("FR" to 125)
                    ),
                    "Scenario2" to ScenarioRequest(11, zones = mapOf("EN" to 10, "FR" to 100))
                )
            )

            assertThrows<IllegalArgumentException>("The distribution of the load across the different zones should equal to 100%") {
                converter.convertConfiguration("my-tenant", request)
            }
        }


    @Test
    internal fun `should successfully convert the minimal request with defined zones`() = testDispatcherProvider.runTest {
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
                hardTimeout = false,
                scenarios = mapOf(
                    "Scenario1" to ScenarioConfiguration(1, DefaultExecutionProfileConfiguration(), zones = mapOf("FR" to 100)),
                    "Scenario2" to ScenarioConfiguration(11, DefaultExecutionProfileConfiguration(), zones = mapOf("EN" to 10, "FR" to 90))
                )
            )
        )
    }

}