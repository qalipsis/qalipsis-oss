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

package io.qalipsis.core.head.inmemory

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.converter.CampaignConfigurationConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class InMemoryCampaignServiceTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    private lateinit var inMemoryCampaignService: InMemoryCampaignService

    @BeforeAll
    internal fun setUp() {
        inMemoryCampaignService = spyk(
            InMemoryCampaignService(
                campaignConfigurationConverter
            )
        )
    }

    @Test
    internal fun `should not schedule a campaign`() = testDispatcherProvider.run {
        // given
        val configuration = CampaignConfiguration(
            name = "This is a campaign",
            speedFactor = 123.2,
            scenarios = mapOf(
                "scenario-1" to ScenarioRequest(1),
                "scenario-2" to ScenarioRequest(3)
            )
        )

        // when
        val exception = assertThrows<IllegalArgumentException> {
            inMemoryCampaignService.schedule("my-tenant", "my-user", configuration)
        }

        // then
        assertThat(exception.message)
            .isEqualTo("In-memory campaign manager does not support scheduling")

        confirmVerified(campaignConfigurationConverter)
    }

}