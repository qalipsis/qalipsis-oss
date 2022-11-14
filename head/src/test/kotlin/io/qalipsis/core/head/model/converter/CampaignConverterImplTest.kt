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

package io.qalipsis.core.head.model.converter

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.repository.CampaignScenarioRepository
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.Scenario
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class CampaignConverterImplTest {
    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @RelaxedMockK
    private lateinit var scenarioRepository: CampaignScenarioRepository

    @InjectMockKs
    private lateinit var converter: CampaignConverterImpl

    @Test
    internal fun `should convert to the model`() = testDispatcherProvider.runTest {
        // given
        coEvery { userRepository.findUsernameById(545) } returns "my-user"
        coEvery { userRepository.findUsernameById(13234) } returns "my-aborter"
        val scenarioVersion1 = Instant.now().minusMillis(3)
        val scenarioVersion2 = Instant.now().minusMillis(542)
        coEvery { scenarioRepository.findByCampaignId(1231243) } returns listOf(
            CampaignScenarioEntity(
                campaignId = 1231243,
                name = "scenario-1",
                minionsCount = 2534
            ).copy(version = scenarioVersion1),
            CampaignScenarioEntity(
                campaignId = 1231243,
                name = "scenario-2",
                minionsCount = 45645
            ).copy(version = scenarioVersion2)
        )
        val version = Instant.now().minusMillis(1)
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)

        val campaignEntity = CampaignEntity(
            id = 1231243,
            version = version,
            tenantId = 4573645,
            key = "my-campaign",
            name = "This is a campaign",
            scheduledMinions = 123,
            timeout = end.plusSeconds(1),
            hardTimeout = true,
            speedFactor = 123.62,
            start = start,
            end = end,
            result = ExecutionStatus.FAILED,
            configurer = 545,
            aborter = 13234
        )

        // when
        val result = converter.convertToModel(campaignEntity)

        // then
        assertThat(result).isEqualTo(
            Campaign(
                creation = result.creation,
                version = version,
                key = "my-campaign",
                name = "This is a campaign",
                speedFactor = 123.62,
                scheduledMinions = 123,
                timeout = end.plusSeconds(1),
                hardTimeout = true,
                start = start,
                end = end,
                status = ExecutionStatus.FAILED,
                configurerName = "my-user",
                aborterName = "my-aborter",
                scenarios = listOf(
                    Scenario(version = scenarioVersion1, name = "scenario-1", minionsCount = 2534),
                    Scenario(version = scenarioVersion2, name = "scenario-2", minionsCount = 45645)
                )
            )
        )
    }

    @Test
    internal fun `should convert to the model without aborter`() = testDispatcherProvider.runTest {
        // given
        coEvery { userRepository.findUsernameById(545) } returns "my-user"
        val scenarioVersion1 = Instant.now().minusMillis(3)
        val scenarioVersion2 = Instant.now().minusMillis(542)
        coEvery { scenarioRepository.findByCampaignId(1231243) } returns listOf(
            CampaignScenarioEntity(
                campaignId = 1231243,
                name = "scenario-1",
                minionsCount = 2534
            ).copy(version = scenarioVersion1),
            CampaignScenarioEntity(
                campaignId = 1231243,
                name = "scenario-2",
                minionsCount = 45645
            ).copy(version = scenarioVersion2)
        )
        val version = Instant.now().minusMillis(1)
        val start = Instant.now().minusSeconds(123)
        val end = Instant.now().minusSeconds(12)

        val campaignEntity = CampaignEntity(
            id = 1231243,
            version = version,
            tenantId = 4573645,
            key = "my-campaign",
            name = "This is a campaign",
            scheduledMinions = 123,
            timeout = end.plusSeconds(1),
            hardTimeout = true,
            speedFactor = 123.62,
            start = start,
            end = end,
            result = ExecutionStatus.FAILED,
            configurer = 545
        )

        // when
        val result = converter.convertToModel(campaignEntity)

        // then
        assertThat(result).isEqualTo(
            Campaign(
                creation = result.creation,
                version = version,
                key = "my-campaign",
                name = "This is a campaign",
                speedFactor = 123.62,
                scheduledMinions = 123,
                timeout = end.plusSeconds(1),
                hardTimeout = true,
                start = start,
                end = end,
                status = ExecutionStatus.FAILED,
                configurerName = "my-user",
                aborterName = null,
                scenarios = listOf(
                    Scenario(version = scenarioVersion1, name = "scenario-1", minionsCount = 2534),
                    Scenario(version = scenarioVersion2, name = "scenario-2", minionsCount = 45645)
                )
            )
        )
    }
}