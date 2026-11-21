/*
 * QALIPSIS
 * Copyright (C) 2023 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.head.inmemory.consolereporter

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.runtime.terminal.TerminalSize
import com.varabyte.kotter.runtime.terminal.inmemory.InMemoryTerminal
import io.mockk.mockk
import io.qalipsis.core.head.inmemory.consolereporter.ConsoleCampaignProgressionReporter.Companion.WIDTH
import io.qalipsis.core.head.inmemory.consolereporter.ConsoleCampaignProgressionReporter.Companion.renderWidth
import org.junit.jupiter.api.Test

internal class ConsoleCampaignProgressionReporterTest {

    @Test
    fun `given a terminal with a known width when computing the render width then it is at least the default width`() {
        assertThat(renderWidth(80)).isEqualTo(WIDTH)
        assertThat(renderWidth(200)).isEqualTo(200)
    }

    @Test
    fun `given a terminal without TTY when rendering the report then the lines are limited to the default width`() {
        val terminal = InMemoryTerminal()
        assertThat(terminal.width).isEqualTo(TerminalSize.Unbounded.width)
        val campaignState = CampaignState("my-campaign").apply {
            scenarios["my-scenario"] = ScenarioState("my-scenario").apply {
                steps["my-step"] = StepState("my-step")
            }
        }
        val renderer = ConsoleRenderer(campaignState, mockk(relaxed = true))

        session(terminal = terminal) {
            section { renderer.render(this, renderWidth(width)) }.run()
        }

        val output = terminal.buffer.toString().replace(ANSI_ESCAPE, "")
        assertThat(output).contains("Scenario: my-scenario")
        assertThat(output.lines().maxOf { it.length }).isLessThanOrEqualTo(WIDTH)
    }

    private companion object {

        val ANSI_ESCAPE = Regex("\u001B\\[[0-9;?]*[A-Za-z]")
    }
}
