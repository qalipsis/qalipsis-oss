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

package io.qalipsis.core.head.report.meters

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class InMemoryCampaignOwnershipCheckerTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var campaignService: CampaignService

    @InjectMockKs
    private lateinit var checker: InMemoryCampaignOwnershipChecker

    @Test
    internal fun `given a current campaign when filtering then only the current campaign is returned`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignService.retrieve("my-tenant", any()) } returns relaxedMockk { every { key } returns "current" }

            // when
            val result = checker.filterOwnedCampaigns("my-tenant", setOf("current", "other"))

            // then
            assertThat(result).containsOnly("current")
        }

    @Test
    internal fun `given no current campaign when filtering then no campaign is returned`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignService.retrieve("my-tenant", any()) } throws NullPointerException()

            // when
            val result = checker.filterOwnedCampaigns("my-tenant", setOf("current"))

            // then
            assertThat(result).isEmpty()
        }
}
