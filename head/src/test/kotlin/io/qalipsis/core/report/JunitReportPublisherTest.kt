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

package io.qalipsis.core.report

import com.google.common.io.Files
import io.mockk.every
import io.mockk.mockkStatic
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.head.report.JunitReportPublisher
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
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

        val campaignKey = "foo"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(
            campaignKey = campaignKey,
            start = start,
            end = end,
            scheduledMinions = 987,
            startedMinions = 123,
            completedMinions = 4231,
            successfulExecutions = 42,
            failedExecutions = 234,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = campaignKey,
                    scenarioName = "bar",
                    start = start,
                    end = end,
                    startedMinions = 2342,
                    completedMinions = 23,
                    successfulExecutions = 4234,
                    failedExecutions = 45,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepName = "normal test",
                            messageId = "1",
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        )
                    )
                )
            ), status = ExecutionStatus.SUCCESSFUL
        )

        val time = getTimeMock()

        //when
        junitReportPublisher.publish(campaignKey, campaignReport)

        //then
        val generatedReport =
            File(generatedReportFolder + "/foo/${campaignReport.scenariosReports[0].scenarioName}.xml").readText()
        val expectedReport =
            File(expectedReportFolder + "/${campaignReport.scenariosReports[0].scenarioName}.xml").readText()
                .replace("__time__", time.toString())

        Assert.assertEquals(expectedReport, generatedReport)
    }

    @Test
    fun `should write report with errors`() = testCoroutineDispatcher.run {
        //given
        val junitReportPublisher = JunitReportPublisher(generatedReportFolder)

        val campaignKey = "foo"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(
            campaignKey = campaignKey,
            start = start,
            end = end,
            scheduledMinions = 987,
            startedMinions = 123,
            completedMinions = 4231,
            successfulExecutions = 42,
            failedExecutions = 234,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = campaignKey,
                    scenarioName = "bar2",
                    start = start,
                    end = end,
                    startedMinions = 2342,
                    completedMinions = 23,
                    successfulExecutions = 4234,
                    failedExecutions = 45,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepName = "normal test",
                            messageId = "1",
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        ),
                        ReportMessage(
                            stepName = "failed test",
                            messageId = "2",
                            severity = ReportMessageSeverity.ERROR,
                            message = "failed"
                        )
                    )
                )
            ), status = ExecutionStatus.SUCCESSFUL
        )

        val time = getTimeMock()

        //when
        junitReportPublisher.publish(campaignKey, campaignReport)


        //then
        val generatedReport =
            File(generatedReportFolder + "/foo/${campaignReport.scenariosReports[0].scenarioName}.xml")
                .readText()
        val expectedReport = File(expectedReportFolder + "/${campaignReport.scenariosReports[0].scenarioName}.xml")
            .readText()
            .replace("__time__", time.toString())

        Assert.assertEquals(expectedReport, generatedReport)
    }

    @Test
    fun `should write few reports with errors`() = testCoroutineDispatcher.run {
        //given
        val junitReportPublisher = JunitReportPublisher(generatedReportFolder)
        val campaignName1 = "foo"
        val campaignName2 = "foo2"

        val timeDiffSeconds = 10L
        val start = Instant.now().minusSeconds(timeDiffSeconds)
        val end = Instant.now()

        val campaignReport = CampaignReport(
            campaignKey = campaignName1,
            start = start,
            end = end,
            scheduledMinions = 987,
            startedMinions = 123,
            completedMinions = 4231,
            successfulExecutions = 42,
            failedExecutions = 234,
            scenariosReports = listOf(
                ScenarioReport(
                    campaignKey = campaignName1,
                    scenarioName = "bar3",
                    start = start,
                    end = end,
                    startedMinions = 1,
                    completedMinions = 1,
                    successfulExecutions = 1,
                    failedExecutions = 1,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepName = "normal test",
                            messageId = "1",
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        ),
                        ReportMessage(
                            stepName = "failed test",
                            messageId = "2",
                            severity = ReportMessageSeverity.ERROR,
                            message = "failed"
                        )
                    )
                ),
                ScenarioReport(
                    campaignKey = campaignName2,
                    scenarioName = "bar4",
                    start = start,
                    end = end,
                    startedMinions = 1,
                    completedMinions = 1,
                    successfulExecutions = 1,
                    failedExecutions = 1,
                    status = ExecutionStatus.SUCCESSFUL,
                    messages = listOf(
                        ReportMessage(
                            stepName = "normal test",
                            messageId = "1",
                            severity = ReportMessageSeverity.INFO,
                            message = "passed"
                        ),
                        ReportMessage(
                            stepName = "failed test",
                            messageId = "2",
                            severity = ReportMessageSeverity.ERROR,
                            message = "failed"
                        )
                    )
                )
            ), status = ExecutionStatus.SUCCESSFUL
        )
        val time = getTimeMock()

        //when
        junitReportPublisher.publish("foo", campaignReport)

        //then
        campaignReport.scenariosReports.forEach {
            val generatedReport = File(generatedReportFolder + "/foo/${it.scenarioName}.xml")
                .readText()
                .replace(Regex("""timestamp="[^"]+""""), """timestamp="$time"""")
            val expectedReport = File(expectedReportFolder + "/${it.scenarioName}.xml")
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