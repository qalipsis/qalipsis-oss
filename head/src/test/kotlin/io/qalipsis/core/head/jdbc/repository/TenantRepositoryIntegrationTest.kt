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
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

internal class TenantRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var tenantRepository: TenantRepository

    val now = Instant.now()

    val tenantPrototype = TenantEntity(
        reference = "my-tenant",
        displayName = "my-tenant-1",
        description = "Here I am",
    )

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())

        // when
        val fetched = tenantRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(TenantEntity::reference).isEqualTo(saved.reference)
            prop(TenantEntity::displayName).isEqualTo(saved.displayName)
            prop(TenantEntity::description).isEqualTo(saved.description)
            prop(TenantEntity::parent).isEqualTo(saved.parent)
        }
        assertThat(fetched!!.creation.toEpochMilli() == saved.creation.toEpochMilli())
    }

    @Test
    fun `should not save two tenants with same reference`() = testDispatcherProvider.run {
        // given
        tenantRepository.save(tenantPrototype.copy())
        assertThrows<R2dbcDataIntegrityViolationException> {
            tenantRepository.save(tenantPrototype.copy())
        }
    }

    @Test
    fun `should find id of tenant by reference`() = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())

        // when
        val fetched = tenantRepository.findIdByReference(saved.reference)

        // then
        assertThat(fetched).isEqualTo(saved.id)
    }

    @Test
    fun `should find reference of the tenant by it id`() = testDispatcherProvider.run {
        // given
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())

        // when
        val fetched = tenantRepository.findReferenceById(saved.id)

        // then
        assertThat(fetched).isEqualTo(saved.reference)
    }

    @Test
    fun `should update the version when the message is updated`() = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())

        // when
        val updated = tenantRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should delete scenario report message on deleteById`(
        factoryRepository: FactoryRepository,
        campaignRepository: CampaignRepository,
        scenarioRepository: ScenarioRepository
    ) = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())
        campaignRepository.save(
            CampaignEntity(
                tenantId = saved.id,
                key = "the-campaign-id",
                name = "This is a campaign",
                scheduledMinions = 345,
                speedFactor = 123.0,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                result = ExecutionStatus.SUCCESSFUL,
                configurer = 1
            )
        )
        val factory = factoryRepository.save(
            FactoryEntity(
                nodeId = "the-node",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "test",
                unicastChannel = "unicast-channel",
                tenantId = saved.id
            )
        )
        scenarioRepository.save(ScenarioEntity(factory.id, "test", 1))

        assertThat(tenantRepository.findAll().count()).isEqualTo(1)
        assertThat(scenarioRepository.findAll().count()).isEqualTo(1)
        assertThat(campaignRepository.findAll().count()).isEqualTo(1)
        assertThat(factoryRepository.findAll().count()).isEqualTo(1)

        // when
        tenantRepository.deleteById(saved.id)

        // then
        assertThat(tenantRepository.findAll().count()).isEqualTo(0)
        assertThat(scenarioRepository.findAll().count()).isEqualTo(0)
        assertThat(campaignRepository.findAll().count()).isEqualTo(0)
        assertThat(factoryRepository.findAll().count()).isEqualTo(0)
    }
}