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

package io.qalipsis.core.head.campaign.states

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.qalipsis.core.campaigns.FactoryConfiguration
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.feedbacks.FailedCampaignFeedback
import org.junit.jupiter.api.Test

internal class AbstractCampaignExecutionStateTest {

    private val campaign = RunningCampaign(tenant = "my-tenant", key = "my-campaign").apply {
        factories["node-1"] = FactoryConfiguration("unicast-1")
    }

    private val state = object : AbstractCampaignExecutionState<CampaignExecutionContext>(campaign) {}

    @Test
    internal fun `given a feedback from a factory of the campaign when checking its origin then it is accepted`() {
        assertThat(state.isEmittedByCampaignFactory(feedback("my-tenant", "my-campaign", "node-1"))).isTrue()
    }

    @Test
    internal fun `given a feedback from a factory out of the campaign when checking its origin then it is rejected`() {
        assertThat(state.isEmittedByCampaignFactory(feedback("my-tenant", "my-campaign", "node-2"))).isFalse()
    }

    @Test
    internal fun `given a feedback claiming another tenant when checking its origin then it is rejected`() {
        assertThat(state.isEmittedByCampaignFactory(feedback("other-tenant", "my-campaign", "node-1"))).isFalse()
    }

    @Test
    internal fun `given a feedback of another campaign when checking its origin then it is rejected`() {
        assertThat(state.isEmittedByCampaignFactory(feedback("my-tenant", "other-campaign", "node-1"))).isFalse()
    }

    private fun feedback(tenant: String, campaignKey: String, nodeId: String) =
        FailedCampaignFeedback(campaignKey = campaignKey, errorMessage = "").also {
            it.tenant = tenant
            it.nodeId = nodeId
        }
}
