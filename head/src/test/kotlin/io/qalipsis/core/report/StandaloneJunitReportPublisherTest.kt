package io.qalipsis.core.report

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.Assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.time.Instant

/**
 * @author rklymenko
 */
@WithMockk
class StandaloneJunitReportPublisherTest {

    @RelaxedMockK
    private lateinit var campaignConfiguration: CampaignConfiguration

    @RelaxedMockK
    private lateinit var campaignStateKeeper: CampaignStateKeeper

    private val reportFolder = "."

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    fun `should write simple report without errors`() = testCoroutineDispatcher.run  {
        //given
        val standaloneJunitReportPublisher = StandaloneJunitReportPublisher(campaignConfiguration, campaignStateKeeper, reportFolder)

        val campaignId = "foo"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(campaignId = campaignId, start = start, end = end, scenariosReports = listOf(
            ScenarioReport(campaignId = campaignId, scenarioId = "bar", start = start, end = end, configuredMinionsCount = 1, executedMinionsCount = 1, stepsCount = 1, successfulExecutions = 1, failedExecutions = 1, status = ExecutionStatus.SUCCESSFUL, messages = listOf(
                ReportMessage(stepId = "normal test", messageId = 1, severity = ReportMessageSeverity.INFO, message = "passed")
            ))
        ), status = ExecutionStatus.SUCCESSFUL)

        coEvery { campaignStateKeeper.report(campaignId) } returns campaignReport


        //when
        standaloneJunitReportPublisher.publish(campaignId)


        //then
        coVerifyOrder {
            campaignStateKeeper.report(campaignId)
        }

        val reportLines = File(reportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml").readLines()

        Assert.assertNotNull(reportLines)

        val testSuiteName = reportLines.filter { it.startsWith("<testsuite") }[0].substringAfter("name=\"")
        Assert.assertTrue(testSuiteName.startsWith(campaignReport.scenariosReports[0].scenarioId))

        val testCount = reportLines.filter { it.startsWith("<testsuite") }[0].substringAfter("tests=\"")
        Assert.assertTrue(testCount.startsWith(campaignReport.scenariosReports.map { it.messages.size }[0].toString()))

        val testFailuresCount = reportLines.filter { it.startsWith("<testsuite") }[0].substringAfter("failures=\"")
        Assert.assertTrue(testFailuresCount.startsWith(campaignReport.scenariosReports[0].messages.filter{ it.severity == ReportMessageSeverity.ABORT}.size.toString()))

        val testErrorsCount = reportLines.filter { it.startsWith("<testsuite") }[0].substringAfter("errors=\"")
        Assert.assertTrue(testErrorsCount.startsWith(campaignReport.scenariosReports[0].messages.filter{ it.severity == ReportMessageSeverity.ERROR}.size.toString()))

        val testTime = reportLines.filter { it.startsWith("<testsuite") }[0].substringAfter("time=\"")
        Assert.assertTrue(testTime.startsWith(timeDiffSeconds.toString()))

        val testCaseName = reportLines.filter { it.trim().startsWith("<testcase") }[0].substringAfter("name=\"")
        Assert.assertTrue(testCaseName.startsWith(campaignReport.scenariosReports[0].messages[0].stepId))
    }

    @Test
    fun `should write report with errors`() = testCoroutineDispatcher.run  {
        //given
        val standaloneJunitReportPublisher = StandaloneJunitReportPublisher(campaignConfiguration, campaignStateKeeper, reportFolder)

        val campaignId = "foo"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(campaignId = campaignId, start = start, end = end, scenariosReports = listOf(
            ScenarioReport(campaignId = campaignId, scenarioId = "bar", start = start, end = end, configuredMinionsCount = 1, executedMinionsCount = 1, stepsCount = 1, successfulExecutions = 1, failedExecutions = 1, status = ExecutionStatus.SUCCESSFUL, messages = listOf(
                ReportMessage(stepId = "normal test", messageId = 1, severity = ReportMessageSeverity.INFO, message = "passed"),
                ReportMessage(stepId = "failed test", messageId = 2, severity = ReportMessageSeverity.ERROR, message = "failed")
            ))
        ), status = ExecutionStatus.SUCCESSFUL)

        coEvery { campaignStateKeeper.report(campaignId) } returns campaignReport


        //when
        standaloneJunitReportPublisher.publish(campaignId)


        //then
        coVerifyOrder {
            campaignStateKeeper.report(campaignId)
        }

        val reportLines = File(reportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml").readLines()

        Assert.assertNotNull(reportLines)

        val testSuiteName = reportLines.filter { it.startsWith("<testsuite") }[0].substringAfter("name=\"")
        Assert.assertTrue(testSuiteName.startsWith(campaignReport.scenariosReports[0].scenarioId))

        val testCases = reportLines.filter { it.trim().startsWith("<testcase") }

        val testCase1name = testCases[0].substringAfter("name=\"")
        Assert.assertTrue(testCase1name.startsWith(campaignReport.scenariosReports[0].messages[0].stepId))

        val testCase2name = testCases[1].substringAfter("name=\"")
        Assert.assertTrue(testCase2name.startsWith(campaignReport.scenariosReports[0].messages[1].stepId))

        val failureMessage = testCases[1].substringAfter("<failure message=\"")
        Assert.assertTrue(failureMessage.startsWith(campaignReport.scenariosReports[0].messages[1].message))

        val failureType = testCases[1].substringAfter("<failure message=\"").substringAfter("type=\"")
        Assert.assertTrue(failureType.startsWith(campaignReport.scenariosReports[0].messages[1].severity.name))
    }

    @Test
    fun `should write few reports with errors`() = testCoroutineDispatcher.run  {
        //given
        val standaloneJunitReportPublisher = StandaloneJunitReportPublisher(campaignConfiguration, campaignStateKeeper, reportFolder)

        val campaignId1 = "foo"
        val campaignId2 = "foo2"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(campaignId = campaignId1, start = start, end = end, scenariosReports = listOf(
            ScenarioReport(campaignId = campaignId1, scenarioId = "bar", start = start, end = end, configuredMinionsCount = 1, executedMinionsCount = 1, stepsCount = 1, successfulExecutions = 1, failedExecutions = 1, status = ExecutionStatus.SUCCESSFUL, messages = listOf(
                ReportMessage(stepId = "normal test", messageId = 1, severity = ReportMessageSeverity.INFO, message = "passed"),
                ReportMessage(stepId = "failed test", messageId = 2, severity = ReportMessageSeverity.ERROR, message = "failed")
            )),
            ScenarioReport(campaignId = campaignId2, scenarioId = "bar2", start = start, end = end, configuredMinionsCount = 1, executedMinionsCount = 1, stepsCount = 1, successfulExecutions = 1, failedExecutions = 1, status = ExecutionStatus.SUCCESSFUL, messages = listOf(
                ReportMessage(stepId = "normal test", messageId = 1, severity = ReportMessageSeverity.INFO, message = "passed"),
                ReportMessage(stepId = "failed test", messageId = 2, severity = ReportMessageSeverity.ERROR, message = "failed")
            ))
        ), status = ExecutionStatus.SUCCESSFUL)

        coEvery { campaignStateKeeper.report(campaignId1) } returns campaignReport


        //when
        standaloneJunitReportPublisher.publish(campaignId1)


        //then
        coVerifyOrder {
            campaignStateKeeper.report(campaignId1)
        }

        campaignReport.scenariosReports.forEach {
            val reportLines = File(reportFolder + "/${it.scenarioId}.xml").readLines()

            Assert.assertNotNull(reportLines)

            val testSuiteName = reportLines.filter { it.startsWith("<testsuite") }[0].substringAfter("name=\"")
            Assert.assertTrue(testSuiteName.startsWith(it.scenarioId))

            val testCases = reportLines.filter { it.trim().startsWith("<testcase") }

            val testCase1name = testCases[0].substringAfter("name=\"")
            Assert.assertTrue(testCase1name.startsWith(it.messages[0].stepId))

            val testCase2name = testCases[1].substringAfter("name=\"")
            Assert.assertTrue(testCase2name.startsWith(it.messages[1].stepId))

            val failureMessage = testCases[1].substringAfter("<failure message=\"")
            Assert.assertTrue(failureMessage.startsWith(it.messages[1].message))

            val failureType = testCases[1].substringAfter("<failure message=\"").substringAfter("type=\"")
            Assert.assertTrue(failureType.startsWith(it.messages[1].severity.name))
        }
    }

    @AfterEach
    fun cleanUp() {
        File(reportFolder).listFiles().filter { it.name.endsWith(".xml") }.forEach { it.delete() }
    }
}