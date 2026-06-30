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

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton
import java.io.File

/**
 * [CampaignReportPublisher] that renders a self-contained HTML execution report and writes it to
 * a local directory when a campaign completes.
 *
 * Activated in standalone or head mode when `report.export.html.enabled=true`.
 *
 * Assembly of [io.qalipsis.core.head.model.CampaignExecutionDetails] is fully delegated to
 * [CampaignReportProvider], which has two implementations:
 * - [io.qalipsis.core.head.inmemory.StandaloneCampaignReportStateKeeperImpl] (standalone) — rebuilds from in-memory state,
 *   including zone distribution per scenario.
 * - [DatabaseCampaignReportProvider] (head + DB) — reads from the database,
 *   including zone distribution per scenario.
 *
 * The output file defaults to `./results/<campaign-key>.html` and can be overridden via
 * `report.export.html.folder`.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.STANDALONE, ExecutionEnvironments.HEAD]),
    Requires(property = "report.export.html.enabled", value = "true", defaultValue = "false")
)
class HtmlCampaignReportPublisher(
    private val campaignReportProvider: CampaignReportProvider,
    private val htmlReportService: HtmlReportService,
    @Property(name = "report.export.html.folder", defaultValue = "./results") private val outputDir: String
) : CampaignReportPublisher {

    override suspend fun publish(tenant: String, campaignKey: CampaignKey, report: CampaignReport) {
        // Phase 1 — assemble the full execution details (zones per scenario, meters, step breakdown).
        // Delegated to the environment-appropriate CampaignReportProvider implementation.
        val campaignExecutionDetails = campaignReportProvider.retrieve(tenant, campaignKey)

        // Phase 2 — render the Thymeleaf template to a self-contained HTML string.
        val html = htmlReportService.render(campaignExecutionDetails.name, listOf(campaignExecutionDetails))

        // Phase 3 — write the rendered HTML to the configured output directory.
        val outputDirectory = File(outputDir).also { it.mkdirs() }
        val outputFile = File(outputDirectory, "${campaignKey}.html")
        outputFile.writeText(html, Charsets.UTF_8)
        log.info { "HTML campaign report written to ${outputFile.absolutePath}" }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    private companion object {
        val log = logger()
    }
}
