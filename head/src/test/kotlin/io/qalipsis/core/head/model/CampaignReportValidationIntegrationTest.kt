package io.qalipsis.core.head.model

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD])
internal class CampaignReportValidationIntegrationTest {

    @Inject
    private lateinit var validator: Validator

    @Test
    fun `campaign key should not be empty`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = emptyList()
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("campaignKey") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must not be blank"))
    }

    @Test
    fun `startedMinions should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "key",
            start = start,
            end = end,
            startedMinions = -1,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = emptyList()
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("startedMinions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `completedMinions should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "key",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = -1,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = emptyList()
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("completedMinions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `successfulExecutions should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "key",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = -1,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = emptyList()
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("successfulExecutions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `failedExecutions in ScenarioReport should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "key",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = -1,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = emptyList()
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("failedExecutions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `campaignKey in ScenarioReport should not be empty`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "",
                    scenarioName = "my-scenario-1",
                    start = start,
                    end = end,
                    startedMinions = 0,
                    completedMinions = 0,
                    successfulExecutions = 0,
                    failedExecutions = 0,
                    status = ExecutionStatus.FAILED,
                    messages = listOf()
                )
            )
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("campaignKey") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must not be blank"))
    }

    @Test
    fun `scenarioName in ScenarioReport should not be empty`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "key",
                    scenarioName = "",
                    start = start,
                    end = end,
                    startedMinions = 0,
                    completedMinions = 0,
                    successfulExecutions = 0,
                    failedExecutions = 0,
                    status = ExecutionStatus.FAILED,
                    messages = listOf()
                )
            )
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("scenarioName") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must not be blank"))
    }

    @Test
    fun `startedMinions in ScenarioReport should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "key",
                    scenarioName = "scenario",
                    start = start,
                    end = end,
                    startedMinions = -1,
                    completedMinions = 0,
                    successfulExecutions = 0,
                    failedExecutions = 0,
                    status = ExecutionStatus.FAILED,
                    messages = listOf()
                )
            )
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("startedMinions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `completedMinions in ScenarioReport should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "key",
                    scenarioName = "scenario",
                    start = start,
                    end = end,
                    startedMinions = 0,
                    completedMinions = -1,
                    successfulExecutions = 0,
                    failedExecutions = 0,
                    status = ExecutionStatus.FAILED,
                    messages = listOf()
                )
            )
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("completedMinions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `successfulExecutions in ScenarioReport should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "key",
                    scenarioName = "scenario",
                    start = start,
                    end = end,
                    startedMinions = 0,
                    completedMinions = 0,
                    successfulExecutions = -1,
                    failedExecutions = 0,
                    status = ExecutionStatus.FAILED,
                    messages = listOf()
                )
            )
        )
        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("successfulExecutions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `failedExecutions should be positive or zero`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "key",
                    scenarioName = "scenario",
                    start = start,
                    end = end,
                    startedMinions = 0,
                    completedMinions = 0,
                    successfulExecutions = 0,
                    failedExecutions = -1,
                    status = ExecutionStatus.FAILED,
                    messages = listOf()
                )
            )
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("failedExecutions") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }

    @Test
    fun `stepName in ReportMessage should not be empty`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "scenario",
                    scenarioName = "my-scenario-1",
                    start = start,
                    end = end,
                    startedMinions = 0,
                    completedMinions = 0,
                    successfulExecutions = 0,
                    failedExecutions = 0,
                    status = ExecutionStatus.FAILED,
                    messages = listOf(
                        ReportMessage(
                            stepName = "",
                            messageId = "message-id-2",
                            severity = ReportMessageSeverity.INFO,
                            message = "Hello from test 2"
                        )
                    )
                )
            )
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("stepName") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must not be blank"))
    }

    @Test
    fun `messageId in ReportMessage should not be empty`() {
        //given
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)
        val report = CampaignReport(
            campaignKey = "the-campaign",
            start = start,
            end = end,
            startedMinions = 0,
            completedMinions = 0,
            successfulExecutions = 0,
            failedExecutions = 0,
            status = ExecutionStatus.SUCCESSFUL,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = "key",
                    scenarioName = "scenario",
                    start = start,
                    end = end,
                    startedMinions = 0,
                    completedMinions = 0,
                    successfulExecutions = 0,
                    failedExecutions = 0,
                    status = ExecutionStatus.FAILED,
                    messages = listOf(
                        ReportMessage(
                            stepName = "my-step-2",
                            messageId = "",
                            severity = ReportMessageSeverity.INFO,
                            message = "Hello from test 2"
                        )
                    )
                )
            )
        )

        //when
        val constraintViolation = validator.validate(report)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("messageId") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must not be blank"))
    }
}