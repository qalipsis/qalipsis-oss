package io.qalipsis.core.head.model.converter

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.campaign.CampaignConfiguration
import io.qalipsis.api.campaign.ScenarioConfiguration
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignRequest
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class CampaignConverterImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @InjectMockKs
    private lateinit var converter: CampaignConverterImpl

    @Test
    internal fun `should convert the request`() = testDispatcherProvider.runTest {
        // given
        every { idGenerator.short() } returns "my-campaign"
        val request = CampaignRequest(
            name = "Anything",
            speedFactor = 1.43,
            startOffsetMs = 123,
            scenarios = mapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        // when
        val result = converter.convertRequest("my-tenant", request)

        // then
        assertThat(result).isDataClassEqualTo(
            CampaignConfiguration(
                tenant = "my-tenant",
                key = "my-campaign",
                speedFactor = 1.43,
                startOffsetMs = 123,
                scenarios = mapOf("Scenario1" to ScenarioConfiguration(1), "Scenario2" to ScenarioConfiguration(11))
            )
        )
    }

    @Test
    internal fun `should convert to the model`() = testDispatcherProvider.runTest {
        // given
        coEvery { userRepository.findUsernameById(545) } returns "my-user"
        val version = Instant.now().minusMillis(1)
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)

        val campaignEntity = CampaignEntity(
            id = 1231243,
            version = version,
            tenantId = 4573645,
            key = "my-campaign",
            name = "This is a campaign",
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
                start = start,
                end = end,
                result = ExecutionStatus.FAILED,
                configurerName = "my-user"
            )
        )
    }
}