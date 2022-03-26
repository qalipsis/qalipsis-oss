package io.qalipsis.core.report

import com.google.common.io.Files
import io.mockk.every
import io.mockk.mockkStatic
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.Assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * @author rklymenko
 */
@WithMockk
internal class JunitReportPublisherTest {

    private val generatedReportFolder = Files.createTempDir().absoluteFile.canonicalPath

    private val expectedReportFolder = "src/test/resources/junit-reports"

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    fun `should write simple report without errors`() = testCoroutineDispatcher.run {
        //given
        val junitReportPublisher = JunitReportPublisher(generatedReportFolder)

        val campaignId = "foo"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(
            campaignId = campaignId, start = start, end = end, scenariosReports = listOf(
                ScenarioReport(
                    campaignId = campaignId,
                    scenarioId = "bar",
                    start = start,
                    end = end,
                    startedMinions = 1,
                    completedMinions = 1,
                    successfulExecutions = 1,
                    failedExecutions = 1,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepId = "normal test",
                            messageId = 1,
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        )
                    )
                )
            ), status = ExecutionStatus.SUCCESSFUL
        )

        val time = getTimeMock()

        //when
        junitReportPublisher.publish(relaxedMockk { every { id } returns campaignId }, campaignReport)

        //then
        val generatedReport =
            File(generatedReportFolder + "/foo/${campaignReport.scenariosReports[0].scenarioId}.xml").readText()
        val expectedReport =
            File(expectedReportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml").readText()
                .replace("__time__", time.toString())

        Assert.assertEquals(expectedReport, generatedReport)
    }

    @Test
    fun `should write report with errors`() = testCoroutineDispatcher.run {
        //given
        val junitReportPublisher = JunitReportPublisher(generatedReportFolder)

        val campaignId = "foo"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(
            campaignId = campaignId, start = start, end = end, scenariosReports = listOf(
                ScenarioReport(
                    campaignId = campaignId,
                    scenarioId = "bar2",
                    start = start,
                    end = end,
                    startedMinions = 1,
                    completedMinions = 1,
                    successfulExecutions = 1,
                    failedExecutions = 1,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepId = "normal test",
                            messageId = 1,
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        ),
                        ReportMessage(
                            stepId = "failed test",
                            messageId = 2,
                            severity = ReportMessageSeverity.ERROR,
                            message = "failed"
                        )
                    )
                )
            ), status = ExecutionStatus.SUCCESSFUL
        )

        val time = getTimeMock()

        //when
        junitReportPublisher.publish(relaxedMockk { every { id } returns campaignId }, campaignReport)


        //then
        val generatedReport = File(generatedReportFolder + "/foo/${campaignReport.scenariosReports[0].scenarioId}.xml")
            .readText()
        val expectedReport = File(expectedReportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml")
            .readText()
            .replace("__time__", time.toString())

        Assert.assertEquals(expectedReport, generatedReport)
    }

    @Test
    fun `should write few reports with errors`() = testCoroutineDispatcher.run {
        //given
        val junitReportPublisher = JunitReportPublisher(generatedReportFolder)
        val campaignId1 = "foo"
        val campaignId2 = "foo2"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(
            campaignId = campaignId1, start = start, end = end, scenariosReports = listOf(
                ScenarioReport(
                    campaignId = campaignId1,
                    scenarioId = "bar3",
                    start = start,
                    end = end,
                    startedMinions = 1,
                    completedMinions = 1,
                    successfulExecutions = 1,
                    failedExecutions = 1,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepId = "normal test",
                            messageId = 1,
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        ),
                        ReportMessage(
                            stepId = "failed test",
                            messageId = 2,
                            severity = ReportMessageSeverity.ERROR,
                            message = "failed"
                        )
                    )
                ),
                ScenarioReport(
                    campaignId = campaignId2,
                    scenarioId = "bar4",
                    start = start,
                    end = end,
                    startedMinions = 1,
                    completedMinions = 1,
                    successfulExecutions = 1,
                    failedExecutions = 1,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepId = "normal test",
                            messageId = 1,
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        ),
                        ReportMessage(
                            stepId = "failed test",
                            messageId = 2,
                            severity = ReportMessageSeverity.ERROR,
                            message = "failed"
                        )
                    )
                )
            ), status = ExecutionStatus.SUCCESSFUL
        )
        val time = getTimeMock()

        //when
        junitReportPublisher.publish(relaxedMockk { every { id } returns "foo" }, campaignReport)

        //then
        campaignReport.scenariosReports.forEach {
            val generatedReport = File(generatedReportFolder + "/foo/${it.scenarioId}.xml")
                .readText()
                .replace(Regex("""timestamp="[^"]+""""), """timestamp="$time"""")
            val expectedReport = File(expectedReportFolder + "/${it.scenarioId}.xml")
                .readText()
                .replace("__time__", time.toString())
            Assert.assertEquals(expectedReport, generatedReport)
        }
    }

    @AfterEach
    fun cleanUp() {
        File(generatedReportFolder).listFiles()!!.filter { it.name.endsWith(".xml") }.forEach { it.delete() }
    }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }
}