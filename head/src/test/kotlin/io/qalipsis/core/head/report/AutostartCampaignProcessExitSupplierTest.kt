/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.report

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.campaign.AutostartCampaignConfiguration
import io.qalipsis.core.head.orchestration.CampaignReportStateKeeper
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class AutostartCampaignProcessExitSupplierTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var autostartCampaignConfiguration: AutostartCampaignConfiguration

    @RelaxedMockK
    private lateinit var campaignReportStateKeeper: CampaignReportStateKeeper

    @InjectMockKs
    private lateinit var autostartCampaignProcessExitSupplier: AutostartCampaignProcessExitSupplier

    @Test
    internal fun `should not return any code when campaign was in success`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.generatedKey } returns "my-campaign"
        coEvery { campaignReportStateKeeper.generateReport("my-campaign")?.status } returns ExecutionStatus.SUCCESSFUL

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    internal fun `should not return any code when campaign was with warning`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.generatedKey } returns "my-campaign"
        coEvery { campaignReportStateKeeper.generateReport("my-campaign")?.status } returns ExecutionStatus.WARNING

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    internal fun `should return 201 when campaign failed`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.generatedKey } returns "my-campaign"
        coEvery { campaignReportStateKeeper.generateReport("my-campaign")?.status } returns ExecutionStatus.FAILED

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.get()).isEqualTo(201)
    }

    @Test
    internal fun `should return 202 when campaign was aborted`() = testDispatcherProvider.runTest {
        // given
        every { autostartCampaignConfiguration.generatedKey } returns "my-campaign"
        coEvery { campaignReportStateKeeper.generateReport("my-campaign")?.status } returns ExecutionStatus.ABORTED

        // when
        val result = autostartCampaignProcessExitSupplier.await()

        // then
        assertThat(result.get()).isEqualTo(202)
    }

    @Test
    internal fun `given no started campaign when awaiting then no code is returned and no report is generated`() =
        testDispatcherProvider.runTest {
            // given
            every { autostartCampaignConfiguration.generatedKey } returns null

            // when
            val result = autostartCampaignProcessExitSupplier.await()

            // then
            assertThat(result.isEmpty).isTrue()
            coVerifyNever { campaignReportStateKeeper.generateReport(any()) }
        }
}
