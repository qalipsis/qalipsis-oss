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
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.Instant
import java.time.ZonedDateTime

@WithMockk
internal class HtmlReportServiceTest {

    @MockK
    private lateinit var templateEngine: TemplateEngine

    @Test
    internal fun `should render using the html-campaign-report template name`() {
        // given
        val capturedContext = slot<Context>()
        every { templateEngine.process(any<String>(), capture(capturedContext)) } returns "<html/>"
        val service = HtmlReportService(templateEngine, "1.0.0")
        val campaigns = listOf(minimalDetails("camp-1"))

        // when
        service.render("My Report", campaigns)

        // then
        verify { templateEngine.process("html-campaign-report", any()) }
        confirmVerified(templateEngine)
    }

    @Test
    internal fun `should set all required context variables`() {
        // given
        val capturedContext = slot<Context>()
        every { templateEngine.process(any<String>(), capture(capturedContext)) } returns "<html/>"
        val service = HtmlReportService(templateEngine, "2.5.1")
        val campaigns = listOf(minimalDetails("camp-1"), minimalDetails("camp-2"))
        val beforeCall = Instant.now()

        // when
        service.render("My Title", campaigns)
        val afterCall = Instant.now()

        // then
        val ctx = capturedContext.captured
        assertThat(ctx.getVariable("title")).isEqualTo("My Title")
        assertThat(ctx.getVariable("campaigns")).isEqualTo(campaigns)
        assertThat(ctx.getVariable("version")).isEqualTo("2.5.1")
        val generatedTime = (ctx.getVariable("generatedTime") as ZonedDateTime).toInstant()
        assertThat(generatedTime).isNotNull()
        // generatedTime should be between beforeCall and afterCall
        assertThat(generatedTime >= beforeCall && generatedTime <= afterCall).isEqualTo(true)
        verify { templateEngine.process(any<String>(), any()) }
        confirmVerified(templateEngine)
    }

    @Test
    internal fun `should return the string produced by the template engine`() {
        // given
        val expectedHtml = "<html><body>Report</body></html>"
        every { templateEngine.process(any<String>(), any()) } returns expectedHtml
        val service = HtmlReportService(templateEngine, "1.0.0")

        // when
        val result = service.render("Title", listOf(minimalDetails("camp-1")))

        // then
        assertThat(result).isEqualTo(expectedHtml)
        verify { templateEngine.process(any<String>(), any()) }
        confirmVerified(templateEngine)
    }

    @Test
    internal fun `should use the version injected at construction time`() {
        // given
        val capturedContext = slot<Context>()
        every { templateEngine.process(any<String>(), capture(capturedContext)) } returns "<html/>"
        val injectedVersion = "0.18.0-SNAPSHOT"
        val service = HtmlReportService(templateEngine, injectedVersion)

        // when
        service.render("Title", emptyList())

        // then
        assertThat(capturedContext.captured.getVariable("version")).isEqualTo(injectedVersion)
        verify { templateEngine.process(any<String>(), any()) }
        confirmVerified(templateEngine)
    }

    @Test
    internal fun `should render actual HTML with expected content at expected locations`() {
        // given — real Thymeleaf engine, no mocks
        val realEngine = TemplateBeanFactory().templateEngine()
        val service = HtmlReportService(realEngine, "1.5.0")
        val campaign = minimalDetails("camp-1") // name="Campaign camp-1", status=SUCCESSFUL

        // when
        val result = service.render("My Report", listOf(campaign))

        // then
        // <title> tag: th:text="${title} + ' — QALIPSIS Campaign Report'"
        assertThat(result).contains("My Report — QALIPSIS Campaign Report")
        // Campaign name in header: th:text="${campaign.name}"
        assertThat(result).contains("Campaign camp-1")
        // Status badge: th:text="${campaign.status?.name() ?: '—'}"
        assertThat(result).contains("SUCCESSFUL")
        // Footer version: th:text="'v' + ${version}"
        assertThat(result).contains("v1.5.0")
        // Footer generated time: 'Generated ' + temporals.format(...) + ' UTC'
        assertThat(result).contains("Generated ")
        assertThat(result).contains(" UTC")
        // Footer title in <code>: th:text="${title}"
        assertThat(result).contains(">My Report<")
    }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    private fun minimalDetails(key: String): CampaignExecutionDetails = CampaignExecutionDetails(
        version = Instant.now(),
        key = key,
        creation = Instant.now(),
        name = "Campaign $key",
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
}
