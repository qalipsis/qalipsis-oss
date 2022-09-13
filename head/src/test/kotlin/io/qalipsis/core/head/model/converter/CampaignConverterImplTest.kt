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
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignRequest
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
                timeoutDurationSec = null,
                hardTimeout = null,
                scenarios = mapOf("Scenario1" to ScenarioConfiguration(1), "Scenario2" to ScenarioConfiguration(11))
            )
        )
    }


    @Test
    internal fun `should convert the complete request`() = testDispatcherProvider.runTest {
        // given
        every { idGenerator.short() } returns "my-campaign"
        val request = CampaignRequest(
            name = "Anything",
            speedFactor = 1.43,
            startOffsetMs = 123,
            timeout = Duration.ofSeconds(2345),
            hardTimeout = true,
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
                timeoutDurationSec = 2345L,
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

    @Test
    internal fun `should convert the report`() = testDispatcherProvider.runTest {
        // given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "my-campaign",
            start = start,
            end = end,
            status = ExecutionStatus.SUCCESSFUL,
            scheduledMinions = 123,
            startedMinions = 12,
            completedMinions = 786,
            successfulExecutions = 456,
            failedExecutions = 321,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario-1",
                    start = start,
                    end = end,
                    startedMinions = null,
                    completedMinions = null,
                    successfulExecutions = null,
                    failedExecutions = null,
                    status = ExecutionStatus.FAILED,
                    messages = listOf(
                        ReportMessage(
                            stepName = "my-step-1",
                            messageId = "message-id-1",
                            severity = ReportMessageSeverity.INFO,
                            message = "Hello from test 1"
                        )
                    )
                ),
                ScenarioReport(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario-2",
                    start = start,
                    end = end,
                    startedMinions = 41,
                    completedMinions = 541,
                    successfulExecutions = 632,
                    failedExecutions = 234,
                    status = ExecutionStatus.ABORTED,
                    messages = listOf(
                        ReportMessage(
                            stepName = "my-step-2",
                            messageId = "message-id-2",
                            severity = ReportMessageSeverity.INFO,
                            message = "Hello from test 2"
                        )
                    )
                )
            )
        )

        // when
        val result = converter.convertReport(report)

        // then
        assertThat(result).isDataClassEqualTo(
            CampaignReport(
                campaignKey = "my-campaign",
                start = start,
                end = end,
                startedMinions = 12,
                completedMinions = 786,
                scheduledMinions = 123,
                successfulExecutions = 456,
                failedExecutions = 321,
                status = ExecutionStatus.SUCCESSFUL,
                scenariosReports = listOf(
                    ScenarioReport(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario-1",
                        start = start,
                        end = end,
                        startedMinions = null,
                        completedMinions = null,
                        successfulExecutions = null,
                        failedExecutions = null,
                        status = ExecutionStatus.FAILED,
                        messages = listOf(
                            ReportMessage(
                                stepName = "my-step-1",
                                messageId = "message-id-1",
                                severity = ReportMessageSeverity.INFO,
                                message = "Hello from test 1"
                            )
                        )
                    ),
                    ScenarioReport(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario-2",
                        start = start,
                        end = end,
                        startedMinions = 41,
                        completedMinions = 541,
                        successfulExecutions = 632,
                        failedExecutions = 234,
                        status = ExecutionStatus.ABORTED,
                        messages = listOf(
                            ReportMessage(
                                stepName = "my-step-2",
                                messageId = "message-id-2",
                                severity = ReportMessageSeverity.INFO,
                                message = "Hello from test 2"
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    internal fun `should convert the incomplete report`() = testDispatcherProvider.runTest {
        // given
        val report = CampaignReport(
            campaignKey = "my-campaign",
            start = null,
            end = null,
            status = ExecutionStatus.QUEUED,
            scheduledMinions = 123,
            startedMinions = null,
            completedMinions = null,
            successfulExecutions = null,
            failedExecutions = null,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario-1",
                    start = null,
                    end = null,
                    startedMinions = null,
                    completedMinions = null,
                    successfulExecutions = null,
                    failedExecutions = null,
                    status = ExecutionStatus.QUEUED,
                    messages = emptyList()
                ),
                ScenarioReport(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario-2",
                    start = null,
                    end = null,
                    startedMinions = null,
                    completedMinions = null,
                    successfulExecutions = null,
                    failedExecutions = null,
                    status = ExecutionStatus.QUEUED,
                    messages = emptyList()
                )
            )
        )

        // when
        val result = converter.convertReport(report)

        // then
        assertThat(result).isDataClassEqualTo(
            CampaignReport(
                campaignKey = "my-campaign",
                start = null,
                end = null,
                scheduledMinions = 123,
                startedMinions = null,
                completedMinions = null,
                successfulExecutions = null,
                failedExecutions = null,
                status = ExecutionStatus.QUEUED,
                scenariosReports = listOf(
                    ScenarioReport(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario-1",
                        start = null,
                        end = null,
                        startedMinions = null,
                        completedMinions = null,
                        successfulExecutions = null,
                        failedExecutions = null,
                        status = ExecutionStatus.QUEUED,
                        messages = emptyList()
                    ),
                    ScenarioReport(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario-2",
                        start = null,
                        end = null,
                        startedMinions = null,
                        completedMinions = null,
                        successfulExecutions = null,
                        failedExecutions = null,
                        status = ExecutionStatus.QUEUED,
                        messages = emptyList()
                    )
                )
            )
        )
    }
}