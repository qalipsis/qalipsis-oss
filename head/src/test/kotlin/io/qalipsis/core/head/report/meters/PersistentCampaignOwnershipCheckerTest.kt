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
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class PersistentCampaignOwnershipCheckerTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    private lateinit var checker: PersistentCampaignOwnershipChecker

    @BeforeEach
    internal fun setUp() {
        checker = PersistentCampaignOwnershipChecker(campaignRepository)
    }

    @Test
    internal fun `given campaigns of several owners when filtering then only the owned ones are returned and cached`() =
        testDispatcherProvider.run {
            // given
            coEvery {
                campaignRepository.findKeyByTenantAndKeyIn("my-tenant", setOf("owned", "foreign"))
            } returns setOf("owned")

            // when
            val firstResult = checker.filterOwnedCampaigns("my-tenant", setOf("owned", "foreign"))
            val secondResult = checker.filterOwnedCampaigns("my-tenant", setOf("owned", "foreign"))

            // then
            assertThat(firstResult).containsOnly("owned")
            assertThat(secondResult).containsOnly("owned")
            coVerifyOnce { campaignRepository.findKeyByTenantAndKeyIn("my-tenant", setOf("owned", "foreign")) }
            confirmVerified(campaignRepository)
        }

    @Test
    internal fun `given cached and unknown campaigns when filtering then only the unknown ones are queried`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("my-tenant", setOf("owned")) } returns setOf("owned")
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("my-tenant", setOf("new")) } returns setOf("new")
            checker.filterOwnedCampaigns("my-tenant", setOf("owned"))

            // when
            val result = checker.filterOwnedCampaigns("my-tenant", setOf("owned", "new"))

            // then
            assertThat(result).containsOnly("owned", "new")
            coVerifyOnce {
                campaignRepository.findKeyByTenantAndKeyIn("my-tenant", setOf("owned"))
                campaignRepository.findKeyByTenantAndKeyIn("my-tenant", setOf("new"))
            }
            confirmVerified(campaignRepository)
        }

    @Test
    internal fun `given a campaign owned by a tenant when filtering for another tenant then it is not returned`() =
        testDispatcherProvider.run {
            // given
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("my-tenant", setOf("owned")) } returns setOf("owned")
            coEvery { campaignRepository.findKeyByTenantAndKeyIn("other-tenant", setOf("owned")) } returns emptySet()
            checker.filterOwnedCampaigns("my-tenant", setOf("owned"))

            // when
            val result = checker.filterOwnedCampaigns("other-tenant", setOf("owned"))

            // then
            assertThat(result).isEmpty()
        }
}
