/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

import io.micronaut.context.annotation.Value
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.StepExecutionDetails
import jakarta.inject.Singleton
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.Instant
import java.time.ZoneOffset

/**
 * Renders one or more [CampaignExecutionDetails] instances to a plain-text ASCII string using the
 * `ascii-campaign-report` Thymeleaf template (TEXT mode).
 *
 * Used by [AsciiReportPublisher] to print the final campaign report to stdout.
 *
 * @author Eric Jessé
 */
@Singleton
class AsciiReportService(
    private val templateEngine: TemplateEngine,
    @Value("\${qalipsis.version:unknown}") private val version: String
) {

    /**
     * Renders [campaigns] into a plain-text ASCII report string.
     *
     * @param title report title shown in the header.
     * @param campaigns one or more campaign execution details to include.
     */
    fun render(title: String, campaigns: Collection<CampaignExecutionDetails>): String {
        val context = Context().apply {
            setVariable("title", title)
            setVariable("campaigns", campaigns)
            setVariable("generatedTime", Instant.now().atZone(ZoneOffset.UTC))
            setVariable("version", version)
            setVariable("UTC", ZoneOffset.UTC)
        }
        return templateEngine.process(TEMPLATE_NAME, context)
            .replace(Regex("^\n+"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trimEnd()
            .plus("\n")
    }

    /**
     * Renders the ASCII details of a single [step] — status, execution counts, messages and meters —
     * mirroring the per-step block emitted by the full ASCII campaign report. Suitable as free-form
     * text content, for example the `<system-out>` of a JUnit test case.
     */
    fun renderStepDetails(step: StepExecutionDetails): String {
        val context = Context().apply { setVariable("step", step) }
        return templateEngine.process(STEP_TEMPLATE_NAME, context)
            .replace(Regex("^\n+"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trimEnd()
    }

    private companion object {
        const val TEMPLATE_NAME = "ascii-campaign-report"
        const val STEP_TEMPLATE_NAME = "ascii-step-details"
    }
}
