/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.qalipsis.core.head.jdbc.entity.TenantEntityForTest
import io.qalipsis.core.head.jdbc.entity.ZoneEntity
import io.qalipsis.core.head.zone.ZoneTenantEntity
import io.qalipsis.core.head.zone.ZoneTenantId
import io.qalipsis.core.postgres.AbstractPostgreSQLTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URL

/**
 * @author Francisca Eze
 */
internal class ZoneRepositoryIntegrationTest : AbstractPostgreSQLTest() {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var tenantRepository: TenantRepositoryForTest

    @Inject
    private lateinit var zoneRepository: ZoneRepository

    @Inject
    private lateinit var zoneTenantRepository: ZoneTenantRepositoryForTest

    private val zone = ZoneEntity(
        key = "EU",
        title = "",
        description = "",
        imagePath = URL("https://images.app.goo.gl/9gtthcy5jgLjb6GW9")
    )

    @BeforeEach
    fun clear() = testDispatcherProvider.run {
        zoneRepository.deleteAll()
        zoneTenantRepository.deleteAll()
    }

    @Test
    fun `should list all the saved zones when they are not tied to a specific tenant`() = testDispatcherProvider.run {
        // given
        val zoneEntity = zoneRepository.save(zone)
        val zoneEntity2 = zoneRepository.save(zone.copy(key = "CH"))
        val zoneEntity3 = zoneRepository.save(zone.copy(key = "AS", description = null, imagePath = null))

        // when
        val fetched = zoneRepository.findZonesByTenant("my-tenant-1")

        // then
        assertThat(fetched.count()).isEqualTo(3)
        assertThat(fetched.toList()).isNotNull().all {
            hasSize(3)
            index(0).isDataClassEqualTo(zoneEntity)
            index(1).isDataClassEqualTo(zoneEntity2)
            index(2).isDataClassEqualTo(zoneEntity3)
        }
    }

    @Test
    fun `should only list the zones allowable to a specific tenant or to no tenant at all`() =
        testDispatcherProvider.run {
            // given
            val tenant1 = tenantRepository.save(TenantEntityForTest(reference = "my-tenant-1"))
            val tenant2 = tenantRepository.save(TenantEntityForTest(reference = "my-tenant-2"))
            val zoneEntity = zoneRepository.save(zone)
            val zoneEntity2 = zoneRepository.save(zone.copy(key = "CH"))
            val zoneEntity3 = zoneRepository.save(zone.copy(key = "AS", description = null, imagePath = null))
            zoneTenantRepository.save(ZoneTenantEntity(ZoneTenantId(zoneId = zoneEntity.id, tenantId = tenant1.id)))
            zoneTenantRepository.save(ZoneTenantEntity(ZoneTenantId(zoneId = zoneEntity3.id, tenantId = tenant2.id)))

            // when
            val fetched = zoneRepository.findZonesByTenant("my-tenant-1")

            // then
            assertThat(fetched.count()).isEqualTo(2)
            assertThat(fetched.toList()).isNotNull().all {
                hasSize(2)
                index(0).isDataClassEqualTo(zoneEntity)
                index(1).isDataClassEqualTo(zoneEntity2)
            }
            assertThat(fetched).doesNotContain(zoneEntity3)
        }
}