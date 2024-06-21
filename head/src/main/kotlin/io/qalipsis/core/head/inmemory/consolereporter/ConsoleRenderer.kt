/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.inmemory.consolereporter

import com.varabyte.kotter.foundation.text.bold
import com.varabyte.kotter.foundation.text.green
import com.varabyte.kotter.foundation.text.p
import com.varabyte.kotter.foundation.text.red
import com.varabyte.kotter.foundation.text.rgb
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import com.varabyte.kotter.foundation.text.yellow
import com.varabyte.kotter.runtime.MainRenderScope
import com.varabyte.kotter.runtime.render.RenderScope
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.math.percentOf
import java.util.concurrent.atomic.AtomicInteger

/**
 * Displays the current state of the campaign into the console.
 * This service is only applicable for the standalone mode.
 */
internal class ConsoleRenderer(
    private val campaignState: CampaignState,
    private val meterReporter: ConsoleMeterReporter
) {

    fun render(renderScope: MainRenderScope, renderWidth: Int) {
        with(renderScope) {
            // Use the official QALIPSIS blue.
            rgb(87, 203, 204) {
                renderTitle(renderWidth)
                renderCampaign(this, campaignState, renderWidth)
                p {
                    textLine("-".repeat(renderWidth))
                }
                renderScenarios(this, campaignState.scenarios, renderWidth)
            }
        }
    }

    /**
     * Renders the title of the report and the overall current status of the campaign.
     */
    private fun RenderScope.renderTitle(renderWidth: Int) {
        val threeColsSize = renderWidth / 3
        p {
            bold {
                val title = if (campaignState.end.get() == null) {
                    "QALIPSIS - A campaign is running".padEnd(threeColsSize)
                } else {
                    "QALIPSIS".padEnd(threeColsSize)
                }
                text(title)
            }

            when {
                campaignState.end.get() == null && campaignState.scenarios.values.any { it.reportableErrors.get() > 0 } ->
                    red { text("RUNNING (failing)".padEnd(threeColsSize)) }

                campaignState.end.get() == null && campaignState.scenarios.values.any { it.reportableWarnings.get() > 0 } ->
                    yellow { text("RUNNING (warnings)".padEnd(threeColsSize)) }

                campaignState.end.get() == null ->
                    green { text("RUNNING".padEnd(threeColsSize)) }

                campaignState.scenarios.values.any { it.reportableErrors.get() > 0 } ->
                    red { text("FAILED".padEnd(threeColsSize)) }

                campaignState.scenarios.values.any { it.reportableWarnings.get() > 0 } ->
                    yellow { text("WITH WARNINGS".padEnd(threeColsSize)) }

                else -> green { text("SUCCESSFUL".padEnd(threeColsSize)) }
            }

            textLine()
        }
    }

    /**
     * Renders the overall information of the campaign.
     */
    private fun renderCampaign(renderScope: RenderScope, campaignState: CampaignState, renderWidth: Int) {
        val threeColsSize = renderWidth / 3
        renderScope.p {
            textLine(campaignState.campaignText)
            textLine(campaignState.scenariosListText)
            text(campaignState.startText.padEnd(threeColsSize))
            text(campaignState.endText.padEnd(threeColsSize))
            textLine(campaignState.durationText)
        }
        renderScope.renderOverallProgression(campaignState.progressionState, renderWidth)
    }

    private fun RenderScope.renderOverallProgression(
        progressionState: ProgressionState,
        renderWidth: Int,
        indent: String = ""
    ) {
        val started = progressionState.startedMinions.get()
        val completed = progressionState.completedMinions.get()
        val scheduled = progressionState.scheduledMinions.get().coerceAtLeast(started)
            .coerceAtLeast(0)
        val threeColsSize = renderWidth / 2

        p {
            bold {
                text("${indent}Minions: $scheduled".padEnd(threeColsSize))
            }
            if (scheduled > 0) {
                text(
                    "Started: $started (${started.percentOf(scheduled)}%)".padEnd(
                        threeColsSize
                    )
                )
                textLine("Completed: $completed (${completed.percentOf(scheduled)}%)")
            }

            val successes = progressionState.successfulSteps.get()
            val failures = progressionState.failedSteps.get()
            val totalExecutions = successes + failures
            if (totalExecutions > 0) {
                bold {
                    text("${indent}Steps executions: $totalExecutions".padEnd(threeColsSize))
                }
                // Passing executions are green.
                green { text("\u2713 $successes (${successes.percentOf(totalExecutions)}%)") }
                // Failing executions are red.
                red { text("    \u2715 $failures (${failures.percentOf(totalExecutions)}%)") }
            }
            textLine()
        }
    }

    /**
     * Renders the details of all the scenarios.
     */
    private fun renderScenarios(
        renderScope: RenderScope,
        scenarios: Map<ScenarioName, ScenarioState>,
        renderWidth: Int
    ) {
        scenarios.toSortedMap().forEach { (name, state) ->
            with(renderScope) {
                renderScenario(name, state, renderWidth)
            }
        }
    }


    /**
     * Renders the details of a single scenario.
     */
    private fun RenderScope.renderScenario(
        scenarioName: ScenarioName,
        scenarioState: ScenarioState,
        renderWidth: Int
    ) {
        val threeColsSize = renderWidth / 2
        bold {
            text("Scenario $scenarioName".padEnd(threeColsSize))
        }
        when {
            scenarioState.end.get() == null && scenarioState.reportableErrors.get() > 0 ->
                red { text("RUNNING (failing)".padEnd(threeColsSize)) }

            scenarioState.end.get() == null && scenarioState.reportableWarnings.get() > 0 ->
                yellow { text("RUNNING (warnings)".padEnd(threeColsSize)) }

            scenarioState.end.get() == null -> green { text("RUNNING".padEnd(threeColsSize)) }
            scenarioState.reportableErrors.get() > 0 -> red { text("FAILED".padEnd(threeColsSize)) }
            scenarioState.reportableWarnings.get() > 0 -> yellow { text("WITH WARNINGS".padEnd(threeColsSize)) }
            else -> green { text("SUCCESSFUL".padEnd(threeColsSize)) }
        }

        scenarioState.endText?.let(this::text)
        textLine()
        if (scenarioState.failedInitialization.get()) {
            red {
                textLine("The initialization of the scenario failed, look at the messages below")
            }
        }
        renderOverallProgression(scenarioState.progressionState, renderWidth, INDENTATION)
        renderErrors(scenarioState.errorsByType)
        renderMessages(scenarioState.messages)

        scenarioState.stepInitializationOrder.distinct().forEach { stepName ->
            scenarioState.steps[stepName]?.let { state -> renderStep(scenarioName, stepName, state, renderWidth) }
        }
    }

    /**
     * Renders the errors by count and percentage.
     */
    private fun RenderScope.renderErrors(errorsByType: Map<Class<out Throwable>, AtomicInteger>) {
        if (errorsByType.isNotEmpty()) {
            val totalErrors = errorsByType.values.sumOf { it.get() }
            red { textLine("${INDENTATION}Errors ($totalErrors)") }
            errorsByType.forEach { (type, count) ->
                textLine("${INDENTATION}${INDENTATION}${type.canonicalName}: $count (${count.percentOf(totalErrors)}%)")
            }
        }
    }

    /**
     * Renders the messages.
     */
    private fun RenderScope.renderMessages(messages: Collection<ExecutionMessage>) {
        if (messages.isNotEmpty()) {
            val distinctMessages = messages.distinct().sortedBy { it.severity }
            val totalMessages = distinctMessages.size
            textLine("${INDENTATION}Messages ($totalMessages)")
            distinctMessages.forEach { (_, severity, message) ->
                text("${INDENTATION}${INDENTATION}")
                when (severity) {
                    ReportMessageSeverity.ABORT -> red { textLine("$severity: $message") }
                    ReportMessageSeverity.ERROR -> red { textLine("$severity: $message") }
                    ReportMessageSeverity.WARN -> yellow { textLine("$severity: $message") }
                    else -> textLine("$severity: $message")
                }
            }
        }
    }


    /**
     * Renders the details of a single scenario.
     */
    private fun RenderScope.renderStep(
        scenarioName: ScenarioName,
        stepName: StepName,
        stepState: StepState,
        renderWidth: Int
    ) {
        val twoColsSize = renderWidth / 2
        text("  \u2699 Step $stepName".padEnd(twoColsSize))
        if (stepState.failedInitialization.get()) {
            red {
                text("Initialization failed, look at the messages below")
            }
        } else {
            // Render the executions.
            val failures = stepState.failedExecutions.get()
            val successes = stepState.successfulExecutions.get()
            val totalExecutions = failures + successes
            text("Executions: $totalExecutions")
            if (totalExecutions > 0) {
                text(" ")
                // Passing executions are green.
                green { text("\u2713 $successes (${successes.percentOf(totalExecutions)}%)") }
                text(" ")
                // Failing executions are red.
                red { text("\u2716 $failures (${failures.percentOf(totalExecutions)}%)") }
            }
        }
        textLine()

        meterReporter.renderStepMeters(this, scenarioName, stepName, renderWidth)

        renderErrors(stepState.errorsByType)
        renderMessages(stepState.messages.values)
    }


    companion object {

        private val INDENTATION = " ".repeat(2)
    }
}