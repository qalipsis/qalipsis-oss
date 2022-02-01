package io.qalipsis.core.report

import assertk.assertThat
import assertk.assertions.isGreaterThan
import com.google.common.io.Files
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.spyk
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.campaign.CampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.time.coMeasureTime
import kotlinx.coroutines.delay
import org.junit.Assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * @author rklymenko
 */
@WithMockk
internal class StandaloneJunitReportPublisherTest {

    @RelaxedMockK
    private lateinit var campaignConfiguration: CampaignConfiguration

    @RelaxedMockK
    private lateinit var campaignStateKeeper: CampaignReportStateKeeper

    private val testCoroutineContext = kotlinx.coroutines.newFixedThreadPoolContext(1, "test-context")

    private val generatedReportFolder = Files.createTempDir().absoluteFile.canonicalPath

    private val expectedReportFolder = "src/test/resources/junit-reports"

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    fun `should write simple report without errors`() = testCoroutineDispatcher.run {
        //given
        val standaloneJunitReportPublisher =
            StandaloneJunitReportPublisher(
                campaignConfiguration,
                campaignStateKeeper,
                testCoroutineContext,
                generatedReportFolder
            )

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

        coEvery { campaignStateKeeper.report(campaignId) } returns campaignReport

        //when
        standaloneJunitReportPublisher.publish(campaignId)

        //then
        coVerifyOrder {
            campaignStateKeeper.report(campaignId)
        }

        val generatedReport =
            File(generatedReportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml").readText()
                .replace(" ", "")
                .replace("\t", "")
        val expectedReport =
            File(expectedReportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml").readText()
                .replace("__time__", time.toString())
                .replace(" ", "")
                .replace("\t", "")

        Assert.assertEquals(expectedReport, generatedReport)
    }

    @Test
    fun `should write report with errors`() = testCoroutineDispatcher.run {
        //given
        val standaloneJunitReportPublisher =
            StandaloneJunitReportPublisher(
                campaignConfiguration,
                campaignStateKeeper,
                testCoroutineContext,
                generatedReportFolder
            )

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

        coEvery { campaignStateKeeper.report(campaignId) } returns campaignReport


        //when
        standaloneJunitReportPublisher.publish(campaignId)


        //then
        coVerifyOrder {
            campaignStateKeeper.report(campaignId)
        }

        val generatedReport = File(generatedReportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml")
            .readText()
            .replace(" ", "")
            .replace("\t", "")
        val expectedReport = File(expectedReportFolder + "/${campaignReport.scenariosReports[0].scenarioId}.xml")
            .readText()
            .replace("__time__", time.toString())
            .replace(" ", "")
            .replace("\t", "")

        Assert.assertEquals(expectedReport, generatedReport)
    }

    @Test
    fun `should write few reports with errors`() = testCoroutineDispatcher.run {
        //given
        val standaloneJunitReportPublisher =
            StandaloneJunitReportPublisher(
                campaignConfiguration,
                campaignStateKeeper,
                testCoroutineContext,
                generatedReportFolder
            )

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

        coEvery { campaignStateKeeper.report(campaignId1) } returns campaignReport

        //when
        standaloneJunitReportPublisher.publish(campaignId1)

        //then
        coVerifyOrder {
            campaignStateKeeper.report(campaignId1)
        }

        campaignReport.scenariosReports.forEach {
            val generatedReport = File(generatedReportFolder + "/${it.scenarioId}.xml")
                .readText()
                .replace(" ", "")
                .replace(Regex("""timestamp="[^"]+""""), """timestamp="$time"""")
                .replace("\t", "")
            val expectedReport = File(expectedReportFolder + "/${it.scenarioId}.xml")
                .readText()
                .replace("__time__", time.toString())
                .replace(" ", "")
                .replace("\t", "")
            Assert.assertEquals(expectedReport, generatedReport)
        }
    }

    @Test
    fun `should call publish and wait for the full execution on leave`() {
        //given
        val standaloneJunitReportPublisher =
            spyk(
                StandaloneJunitReportPublisher(
                    campaignConfiguration,
                    campaignStateKeeper,
                    testCoroutineContext,
                    generatedReportFolder
                ), recordPrivateCalls = true
            )
        val campaignId = "boo"
        coEvery { standaloneJunitReportPublisher.publish(any()) } coAnswers { delay(300) }
        coEvery { campaignConfiguration.id } returns campaignId

        //when
        val elapsedTime = coMeasureTime {
            standaloneJunitReportPublisher.publishOnLeave()
        }

        //then
        assertThat(elapsedTime).isGreaterThan(Duration.ofMillis(250))
        coVerifyOrder {
            standaloneJunitReportPublisher.publish(campaignId)
        }
    }

    @AfterEach
    fun cleanUp() {
        File(generatedReportFolder).listFiles().filter { it.name.endsWith(".xml") }.forEach { it.delete() }
    }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }
}