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
import io.micronaut.context.annotation.Value
import io.micronaut.core.order.Ordered
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.CampaignReportPublisher
import io.qalipsis.core.configuration.ExecutionEnvironments.HEAD
import io.qalipsis.core.configuration.ExecutionEnvironments.STANDALONE
import jakarta.inject.Singleton
import java.io.File

/**
 * [CampaignReportPublisher] that renders the campaign report as a plain-text ASCII summary.
 *
 * Two optional outputs, independently controlled:
 * - **Console** (`report.export.console.enabled`, default `true`): prints the report to stdout.
 * - **File** (`report.export.ascii.enabled`, default `false`): writes a `.txt` file to
 *   `report.export.ascii.folder` (default `./results`).
 *
 * The bean is always registered in standalone or head mode. When both outputs are disabled
 * at runtime the publisher is a no-op.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(bean = CampaignReportProvider::class),
    Requires(env = [HEAD, STANDALONE])
)
class AsciiReportPublisher(
    private val campaignReportProvider: CampaignReportProvider,
    private val asciiReportService: AsciiReportService,
    @Value("\${report.export.console.enabled:true}") private val consoleEnabled: Boolean,
    @Property(name = "report.export.console.folder", defaultValue = "") private val outputDir: String,
) : CampaignReportPublisher {

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override suspend fun publish(tenant: String, campaignKey: CampaignKey, report: CampaignReport) {
        if (consoleEnabled || outputDir.isNotBlank()) {
            val details = campaignReportProvider.retrieve(tenant, campaignKey)
            val ascii = asciiReportService.render(details.name, listOf(details))

            if (consoleEnabled) {
                println(ascii)
            }
            if (outputDir.isNotBlank()) {
                val outputDirectory = File(outputDir).also { it.mkdirs() }
                val outputFile = File(outputDirectory, "$campaignKey.txt")
                outputFile.writeText(ascii, Charsets.UTF_8)
                log.info { "ASCII campaign report written to ${outputFile.absolutePath}" }
            }
        }
    }

    private companion object {
        val log = logger()
    }
}
