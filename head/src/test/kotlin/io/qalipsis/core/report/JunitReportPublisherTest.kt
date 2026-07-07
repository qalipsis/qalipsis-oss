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

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.StepExecutionDetails
import io.qalipsis.core.head.report.AsciiReportService
import io.qalipsis.core.head.report.CampaignReportProvider
import io.qalipsis.core.head.report.JunitReportPublisher
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

@WithMockk
internal class JunitReportPublisherTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var campaignReportProvider: CampaignReportProvider

    @MockK
    private lateinit var asciiReportService: AsciiReportService

    @TempDir
    private lateinit var tempDir: File

    private lateinit var publisher: JunitReportPublisher

    @BeforeEach
    internal fun setUp() {
        publisher = JunitReportPublisher(
            campaignReportProvider = campaignReportProvider,
            asciiReportService = asciiReportService,
            outputDir = tempDir.absolutePath
        )
        every { asciiReportService.renderStepDetails(any()) } answers {
            "details of ${firstArg<StepExecutionDetails>().name}"
        }
    }

    @Test
    internal fun `should write one testsuite per scenario and one testcase per step`() =
        testDispatcherProvider.runTest {
            // given
            val start = Instant.parse("2026-01-01T10:00:00Z")
            val end = Instant.parse("2026-01-01T10:00:12Z")
            val details = campaignExecutionDetails(
                key = "camp-1",
                name = "My Campaign",
                start = start,
                end = end,
                scenarios = listOf(
                    scenarioExecutionDetails(
                        name = "scenario-a",
                        start = start,
                        end = end,
                        steps = listOf(
                            executedStep("step-1"),
                            executedStep("step-2")
                        )
                    ),
                    scenarioExecutionDetails(
                        name = "scenario-b",
                        start = start,
                        end = end,
                        steps = listOf(executedStep("step-3"))
                    )
                )
            )
            coEvery { campaignReportProvider.retrieve("my-tenant", "camp-1") } returns details

            // when
            publisher.publish("my-tenant", "camp-1", mockk<CampaignReport>())

            // then
            val xml = File(tempDir, "camp-1.xml").readText()
            assertThat(xml).contains("""<testsuites id="camp-1" name="My Campaign"""")
            assertThat(xml).contains("""tests="3"""")
            assertThat(xml).contains("""<testsuite id="camp-1-scenario-a" name="scenario-a"""")
            assertThat(xml).contains("""<testsuite id="camp-1-scenario-b" name="scenario-b"""")
            assertThat(xml).contains("""<testcase name="step-1" classname="scenario-a"""")
            assertThat(xml).contains("""<testcase name="step-2" classname="scenario-a"""")
            assertThat(xml).contains("""<testcase name="step-3" classname="scenario-b"""")
        }

    @Test
    internal fun `should mark a step with no execution as skipped`() = testDispatcherProvider.runTest {
        // given
        val details = campaignExecutionDetails(
            key = "camp-2",
            name = "camp-2",
            scenarios = listOf(
                scenarioExecutionDetails(
                    name = "scenario",
                    steps = listOf(skippedStep("skipped-step"), executedStep("normal-step"))
                )
            )
        )
        coEvery { campaignReportProvider.retrieve("my-tenant", "camp-2") } returns details

        // when
        publisher.publish("my-tenant", "camp-2", mockk<CampaignReport>())

        // then
        val xml = File(tempDir, "camp-2.xml").readText()
        val skippedTestCase = xml.substringAfter("""<testcase name="skipped-step"""").substringBefore("</testcase>")
        assertThat(skippedTestCase).contains("""<skipped message="Step not executed"/>""")
        val normalTestCase = xml.substringAfter("""<testcase name="normal-step"""").substringBefore("</testcase>")
        assertThat(normalTestCase).doesNotContain("<skipped")
        assertThat(xml).contains("""skipped="1"""")
    }

    @Test
    internal fun `should map ERROR messages to failure and ABORT messages to error`() = testDispatcherProvider.runTest {
        // given
        val details = campaignExecutionDetails(
            key = "camp-3",
            name = "camp-3",
            scenarios = listOf(
                scenarioExecutionDetails(
                    name = "scenario",
                    steps = listOf(
                        executedStep(
                            name = "faulty",
                            messages = listOf(
                                reportMessage("faulty", ReportMessageSeverity.ERROR, "assertion failed"),
                                reportMessage("faulty", ReportMessageSeverity.ABORT, "connection lost")
                            )
                        )
                    )
                )
            )
        )
        coEvery { campaignReportProvider.retrieve("my-tenant", "camp-3") } returns details

        // when
        publisher.publish("my-tenant", "camp-3", mockk<CampaignReport>())

        // then
        val xml = File(tempDir, "camp-3.xml").readText()
        assertThat(xml).contains("""<failure message="assertion failed" type="ERROR">""")
        assertThat(xml).contains("""<error message="connection lost" type="ABORT">""")
        assertThat(xml).contains("""failures="1"""")
        assertThat(xml).contains("""errors="1"""")
    }

    @Test
    internal fun `should embed the ASCII step details rendered by AsciiReportService as system-out`() =
        testDispatcherProvider.runTest {
            // given
            val details = campaignExecutionDetails(
                key = "camp-4",
                name = "camp-4",
                scenarios = listOf(
                    scenarioExecutionDetails(
                        name = "scenario",
                        steps = listOf(executedStep(name = "step-x"))
                    )
                )
            )
            coEvery { campaignReportProvider.retrieve("my-tenant", "camp-4") } returns details

            // when
            publisher.publish("my-tenant", "camp-4", mockk<CampaignReport>())

            // then
            val xml = File(tempDir, "camp-4.xml").readText()
            assertThat(xml).contains("<system-out>")
            assertThat(xml).contains("<![CDATA[details of step-x]]>")
        }

    @Test
    internal fun `should escape XML special characters in attributes and CDATA terminators in text`() =
        testDispatcherProvider.runTest {
            // given
            every { asciiReportService.renderStepDetails(any()) } returns "raw ]]> inside"
            val details = campaignExecutionDetails(
                key = "camp-5",
                name = """A & B <"C">""",
                scenarios = listOf(
                    scenarioExecutionDetails(
                        name = "sc",
                        steps = listOf(
                            executedStep(
                                name = "step",
                                messages = listOf(
                                    reportMessage("step", ReportMessageSeverity.ERROR, "boom <tag> & \"quote\"")
                                )
                            )
                        )
                    )
                )
            )
            coEvery { campaignReportProvider.retrieve("my-tenant", "camp-5") } returns details

            // when
            publisher.publish("my-tenant", "camp-5", mockk<CampaignReport>())

            // then
            val xml = File(tempDir, "camp-5.xml").readText()
            assertThat(xml).contains("""name="A &amp; B &lt;&quot;C&quot;&gt;"""")
            assertThat(xml).contains("""message="boom &lt;tag&gt; &amp; &quot;quote&quot;"""")
            // CDATA payload was split around the ]]> terminator to keep the XML valid.
            assertThat(xml).contains("]]]]><![CDATA[>")
        }

    @Test
    internal fun `should create the output directory when it does not exist`() = testDispatcherProvider.runTest {
        // given
        val nonExistingSubDir = File(tempDir, "sub/dir/nested")
        val publisherWithNewDir = JunitReportPublisher(
            campaignReportProvider = campaignReportProvider,
            asciiReportService = asciiReportService,
            outputDir = nonExistingSubDir.absolutePath
        )
        val details = campaignExecutionDetails(
            key = "camp-6",
            name = "camp-6",
            scenarios = listOf(scenarioExecutionDetails("sc", steps = listOf(executedStep("step"))))
        )
        coEvery { campaignReportProvider.retrieve("my-tenant", "camp-6") } returns details

        // when
        publisherWithNewDir.publish("my-tenant", "camp-6", mockk<CampaignReport>())

        // then
        assertThat(File(nonExistingSubDir, "camp-6.xml").exists()).isTrue()
    }

    @Test
    internal fun `should compute duration in seconds between campaign start and end`() =
        testDispatcherProvider.runTest {
            // given
            val start = Instant.parse("2026-01-01T10:00:00Z")
            val end = Instant.parse("2026-01-01T10:00:42Z")
            val details = campaignExecutionDetails(
                key = "camp-7",
                name = "camp-7",
                start = start,
                end = end,
                scenarios = listOf(
                    scenarioExecutionDetails(
                        "sc",
                        start = start,
                        end = end,
                        steps = listOf(executedStep("step"))
                    )
                )
            )
            coEvery { campaignReportProvider.retrieve("my-tenant", "camp-7") } returns details

            // when
            publisher.publish("my-tenant", "camp-7", mockk<CampaignReport>())

            // then
            val xml = File(tempDir, "camp-7.xml").readText()
            assertThat(xml).contains("""<testsuites id="camp-7"""")
            assertThat(xml.substringAfter("<testsuites").substringBefore(">")).contains("""time="42"""")
            assertThat(xml.substringAfter("<testsuite ").substringBefore(">")).contains("""time="42"""")
        }

    private fun campaignExecutionDetails(
        key: String,
        name: String,
        start: Instant? = null,
        end: Instant? = null,
        scenarios: List<ScenarioExecutionDetails> = emptyList()
    ): CampaignExecutionDetails = CampaignExecutionDetails(
        version = Instant.now(),
        key = key,
        creation = Instant.now(),
        name = name,
        speedFactor = 1.0,
        scheduledMinions = null,
        start = start,
        end = end,
        status = ExecutionStatus.SUCCESSFUL,
        zones = emptySet(),
        startedMinions = null,
        completedMinions = null,
        successfulExecutions = null,
        failedExecutions = null,
        scenarios = scenarios,
        meters = emptyList()
    )

    private fun scenarioExecutionDetails(
        name: String,
        start: Instant? = null,
        end: Instant? = null,
        steps: List<StepExecutionDetails> = emptyList()
    ): ScenarioExecutionDetails = ScenarioExecutionDetails(
        id = name,
        name = name,
        start = start,
        end = end,
        startedMinions = null,
        completedMinions = null,
        successfulExecutions = null,
        failedExecutions = null,
        status = ExecutionStatus.SUCCESSFUL,
        messages = emptyList(),
        steps = steps
    )

    private fun executedStep(
        name: String,
        messages: List<ReportMessage> = emptyList()
    ): StepExecutionDetails = StepExecutionDetails(
        name = name,
        status = ExecutionStatus.SUCCESSFUL,
        successfulExecutions = 1L,
        failedExecutions = 0L,
        messages = messages
    )

    private fun skippedStep(name: String): StepExecutionDetails = StepExecutionDetails(
        name = name,
        status = ExecutionStatus.SUCCESSFUL,
        successfulExecutions = 0L,
        failedExecutions = 0L
    )

    private fun reportMessage(
        stepName: String,
        severity: ReportMessageSeverity,
        message: String
    ): ReportMessage = ReportMessage(
        stepName = stepName,
        messageId = "$stepName-$severity",
        severity = severity,
        message = message
    )
}
