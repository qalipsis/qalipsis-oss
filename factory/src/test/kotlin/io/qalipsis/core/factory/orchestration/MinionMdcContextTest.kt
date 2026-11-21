/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.orchestration

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.slf4j.MDC

@Timeout(5)
internal class MinionMdcContextTest {

    @AfterEach
    fun cleanMdc() {
        MDC.clear()
    }

    @Test
    fun `given an empty MDC when the coroutine runs then the minion keys are set and removed when it leaves`() {
        // when
        val valuesInCoroutine = runBlocking(MinionMdcContext("my-campaign", "my-scenario", "my-minion", "12")) {
            val beforeSuspension = mdcValues()
            yield()
            beforeSuspension to mdcValues()
        }

        // then
        val expected = listOf("my-campaign", "my-scenario", "my-minion", "12")
        assertThat(valuesInCoroutine.first).isEqualTo(expected)
        assertThat(valuesInCoroutine.second).isEqualTo(expected)
        assertThat(mdcValues()).isEqualTo(listOf(null, null, null, null))
    }

    @Test
    fun `given values in the MDC of the thread when the coroutine suspends then the previous values are restored`() {
        // given
        MDC.put("campaign", "outer-campaign")
        MDC.put("minion", "outer-minion")
        MDC.put("other", "untouched")

        runBlocking {
            // when
            val job = launch(MinionMdcContext("my-campaign", "my-scenario", "my-minion", "12"), CoroutineStart.UNDISPATCHED) {
                assertThat(mdcValues()).isEqualTo(listOf("my-campaign", "my-scenario", "my-minion", "12"))
                yield()
                assertThat(mdcValues()).isEqualTo(listOf("my-campaign", "my-scenario", "my-minion", "12"))
            }

            // then
            // The job is suspended: the outer coroutine sees the previous values.
            assertThat(mdcValues()).isEqualTo(listOf("outer-campaign", null, "outer-minion", null))
            job.join()
        }
        assertThat(mdcValues()).isEqualTo(listOf("outer-campaign", null, "outer-minion", null))
        assertThat(MDC.get("other")).isEqualTo("untouched")
    }

    @Test
    fun `given a minion when launching a job then the job logs with the identifiers of the minion`() {
        // given
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", pauseAtStart = false)
        var valuesInJob: List<String?> = emptyList()

        // when
        runBlocking {
            minion.launch(this) { valuesInJob = mdcValues() }!!.join()
        }

        // then
        assertThat(valuesInJob).isEqualTo(listOf("my-campaign", "my-scenario", "my-minion", "0"))
        assertThat(MDC.get("job")).isNull()
    }

    private fun mdcValues() = listOf(MDC.get("campaign"), MDC.get("scenario"), MDC.get("minion"), MDC.get("job"))
}
