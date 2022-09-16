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

package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class CampaignFactoryRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignFactoryRepository: CampaignFactoryRepository

    @Inject
    private lateinit var tenantRepository: TenantRepository

    private val campaignPrototype =
        CampaignEntity(
            key = "the-campaign-id",
            name = "This is a campaign",
            speedFactor = 123.0,
            start = Instant.now() - Duration.ofSeconds(173),
            end = Instant.now(),
            scheduledMinions = 345,
            result = ExecutionStatus.SUCCESSFUL,
            configurer = 1
        )

    private val factoryPrototype =
        FactoryEntity(
            nodeId = "the-node-id",
            registrationTimestamp = Instant.now(),
            registrationNodeId = "the-registration-node-id",
            unicastChannel = "unicast-channel"
        )

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        factoryRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    internal fun `should save then update the discarded flag`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
            val campaign = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))
            val factory1 = factoryRepository.save(factoryPrototype.copy(tenantId = tenant.id))
            val factory2 = factoryRepository.save(factoryPrototype.copy(nodeId = "other-factory", tenantId = tenant.id))
            val saved1 =
                campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory1.id, discarded = false))
            val saved2 =
                campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory2.id, discarded = false))

            // when
            var fetched = campaignFactoryRepository.findById(saved1.id)

            // then
            assertThat(fetched).isNotNull().isDataClassEqualTo(saved1)

            // when
            campaignFactoryRepository.discard(campaign.id, listOf(factory1.id))

            // then
            fetched = campaignFactoryRepository.findById(saved1.id)
            assertThat(fetched).isNotNull().all {
                prop(CampaignFactoryEntity::discarded).isTrue()
                prop(CampaignFactoryEntity::version).isGreaterThan(saved1.version)
            }
            // The record for factory 2 remains unchanged.
            fetched = campaignFactoryRepository.findById(saved2.id)
            assertThat(fetched).isNotNull().all {
                prop(CampaignFactoryEntity::discarded).isFalse()
                prop(CampaignFactoryEntity::version).isEqualTo(saved2.version)
            }
        }

    @Test
    fun `should update the version when the entity is updated`() =
        testDispatcherProvider.run {
            // given
            val tenant = tenantRepository.save(TenantEntity(Instant.now(), "my-tenant", "test-tenant"))
            val campaign = campaignRepository.save(campaignPrototype.copy(tenantId = tenant.id))
            val factory = factoryRepository.save(factoryPrototype.copy(tenantId = tenant.id))
            val saved =
                campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory.id, discarded = false))

            // when
            val updated = campaignFactoryRepository.update(saved)

            // then
            assertThat(updated.version).isGreaterThan(saved.version)
        }

}