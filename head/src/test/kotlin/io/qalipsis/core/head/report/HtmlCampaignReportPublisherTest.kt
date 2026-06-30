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

package io.qalipsis.core.head.report

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

@WithMockk
internal class HtmlCampaignReportPublisherTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var campaignReportProvider: CampaignReportProvider

    @MockK
    private lateinit var htmlReportService: HtmlReportService

    @TempDir
    private lateinit var tempDir: File

    private lateinit var publisher: HtmlCampaignReportPublisher

    @BeforeEach
    internal fun setUp() {
        publisher = HtmlCampaignReportPublisher(
            campaignReportProvider = campaignReportProvider,
            htmlReportService = htmlReportService,
            outputDir = tempDir.absolutePath
        )
    }

    private fun minimalDetails(key: String, name: String): CampaignExecutionDetails = CampaignExecutionDetails(
        version = Instant.now(),
        key = key,
        creation = Instant.now(),
        name = name,
        speedFactor = 1.0,
        scheduledMinions = null,
        start = null,
        end = null,
        status = ExecutionStatus.SUCCESSFUL,
        zones = emptySet(),
        startedMinions = null,
        completedMinions = null,
        successfulExecutions = null,
        failedExecutions = null,
        scenarios = emptyList(),
        meters = emptyList()
    )

    @Test
    internal fun `should retrieve campaign execution details from the provider`() = testDispatcherProvider.runTest {
        // given
        val report = mockk<CampaignReport>()
        val details = minimalDetails("camp-1", "My Campaign")
        coEvery { campaignReportProvider.retrieve("my-tenant", "camp-1") } returns details
        coEvery { htmlReportService.render(any(), any()) } returns "<html/>"

        // when
        publisher.publish("my-tenant", "camp-1", report)

        // then
        coVerifyOrder {
            campaignReportProvider.retrieve("my-tenant", "camp-1")
            htmlReportService.render(any(), any())
        }
        confirmVerified(campaignReportProvider, htmlReportService)
    }

    @Test
    internal fun `should render HTML using the campaign name and the retrieved details`() =
        testDispatcherProvider.runTest {
            // given
            val report = mockk<CampaignReport>()
            val details = minimalDetails("camp-1", "My Campaign")
            coEvery { campaignReportProvider.retrieve("my-tenant", "camp-1") } returns details
            coEvery { htmlReportService.render("My Campaign", listOf(details)) } returns "<html>rendered</html>"

            // when
            publisher.publish("my-tenant", "camp-1", report)

            // then
            coVerifyOrder {
                campaignReportProvider.retrieve("my-tenant", "camp-1")
                htmlReportService.render("My Campaign", listOf(details))
            }
            confirmVerified(campaignReportProvider, htmlReportService)
        }

    @Test
    internal fun `should write the rendered HTML to the output file named after the campaign key`() =
        testDispatcherProvider.runTest {
            // given
            val report = mockk<CampaignReport>()
            val details = minimalDetails("camp-42", "My Campaign")
            val expectedHtml = "<html>report content</html>"
            coEvery { campaignReportProvider.retrieve("my-tenant", "camp-42") } returns details
            coEvery { htmlReportService.render(any(), any()) } returns expectedHtml

            // when
            publisher.publish("my-tenant", "camp-42", report)

            // then
            val outputFile = File(tempDir, "camp-42.html")
            assertThat(outputFile.exists()).isTrue()
            assertThat(outputFile.readText(Charsets.UTF_8)).isEqualTo(expectedHtml)
            coVerify {
                campaignReportProvider.retrieve("my-tenant", "camp-42")
                htmlReportService.render(any(), any())
            }
            confirmVerified(campaignReportProvider, htmlReportService)
        }

    @Test
    internal fun `should create the output directory when it does not exist`() = testDispatcherProvider.runTest {
        // given
        val nonExistingSubDir = File(tempDir, "sub/dir/nested")
        val publisherWithNewDir = HtmlCampaignReportPublisher(
            campaignReportProvider = campaignReportProvider,
            htmlReportService = htmlReportService,
            outputDir = nonExistingSubDir.absolutePath
        )
        val report = mockk<CampaignReport>()
        val details = minimalDetails("camp-1", "My Campaign")
        coEvery { campaignReportProvider.retrieve("my-tenant", "camp-1") } returns details
        coEvery { htmlReportService.render(any(), any()) } returns "<html/>"

        // when
        publisherWithNewDir.publish("my-tenant", "camp-1", report)

        // then
        assertThat(nonExistingSubDir.exists()).isTrue()
        assertThat(File(nonExistingSubDir, "camp-1.html").exists()).isTrue()
        coVerify {
            campaignReportProvider.retrieve("my-tenant", "camp-1")
            htmlReportService.render(any(), any())
        }
        confirmVerified(campaignReportProvider, htmlReportService)
    }
}
