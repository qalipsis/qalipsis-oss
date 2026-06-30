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

import io.micronaut.context.annotation.Value
import io.qalipsis.core.head.model.CampaignExecutionDetails
import jakarta.inject.Singleton
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.Instant
import java.time.ZoneOffset

/**
 * Renders one or more [CampaignExecutionDetails] instances to a self-contained HTML string using the
 * `html-campaign-report` Thymeleaf template.
 *
 * Shared by [HtmlCampaignReportPublisher] (writes to file) and the REST download endpoint.
 *
 * @author Eric Jessé
 */
@Singleton
class HtmlReportService(
    private val templateEngine: TemplateEngine,
    @Value("\${qalipsis.version:unknown}") private val version: String
) {

    /**
     * Renders the [campaigns] into a self-contained HTML string.
     *
     * @param title the report title shown in the page header and `<title>` tag.
     * @param campaigns one or more campaign execution details to include in the report.
     */
    fun render(title: String, campaigns: Collection<CampaignExecutionDetails>): String {
        val context = Context().apply {
            setVariable("title", title)
            setVariable("campaigns", campaigns)
            setVariable("generatedTime", Instant.now().atZone(ZoneOffset.UTC))
            setVariable("UTC", ZoneOffset.UTC)
            setVariable("version", version)
        }
        return templateEngine.process(TEMPLATE_NAME, context)
    }

    private companion object {
        const val TEMPLATE_NAME = "html-campaign-report"
    }
}
