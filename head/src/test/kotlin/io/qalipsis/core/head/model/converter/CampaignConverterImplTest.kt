package io.qalipsis.core.head.model.converter

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.campaigns.ScenarioConfiguration
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

@WithMockk
internal class CampaignConverterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var scenarioRepository: CampaignScenarioRepository

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var converter: CampaignConverterImpl

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
                scenarios = mapOf("Scenario1" to ScenarioConfiguration(1), "Scenario2" to ScenarioConfiguration(11))
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
                hardTimeout = true,
                scenarios = mapOf("Scenario1" to ScenarioConfiguration(1), "Scenario2" to ScenarioConfiguration(11))
            )
        )
    }

    @Test
    internal fun `should convert to the model`() = testDispatcherProvider.runTest {
        // given
        coEvery { userRepository.findUsernameById(545) } returns "my-user"
        val scenarioVersion1 = Instant.now().minusMillis(3)
        val scenarioVersion2 = Instant.now().minusMillis(542)
        coEvery { scenarioRepository.findByCampaignId(1231243) } returns listOf(
            CampaignScenarioEntity(
                campaignId = 1231243,
                name = "scenario-1",
                minionsCount = 2534
            ).copy(version = scenarioVersion1),
            CampaignScenarioEntity(
                campaignId = 1231243,
                name = "scenario-2",
                minionsCount = 45645
            ).copy(version = scenarioVersion2)
        )
        val version = Instant.now().minusMillis(1)
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)

        val campaignEntity = CampaignEntity(
            id = 1231243,
            version = version,
            tenantId = 4573645,
            key = "my-campaign",
            name = "This is a campaign",
            scheduledMinions = 123,
            timeout = end.plusSeconds(1),
            hardTimeout = true,
            speedFactor = 123.62,
            start = start,
            end = end,
            result = ExecutionStatus.FAILED,
            configurer = 545
        )

        // when
        val result = converter.convertToModel(campaignEntity)

        // then
        assertThat(result).isDataClassEqualTo(
            Campaign(
                version = version,
                key = "my-campaign",
                name = "This is a campaign",
                speedFactor = 123.62,
                scheduledMinions = 123,
                timeout = end.plusSeconds(1),
                hardTimeout = true,
                start = start,
                end = end,
                result = ExecutionStatus.FAILED,
                configurerName = "my-user",
                scenarios = listOf(
                    Scenario(version = scenarioVersion1, name = "scenario-1", minionsCount = 2534),
                    Scenario(version = scenarioVersion2, name = "scenario-2", minionsCount = 45645)
                )
            )
        )
    }

}