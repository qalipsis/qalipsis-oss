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

package io.qalipsis.core.head.zone

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.qalipsis.core.head.jdbc.entity.ZoneEntity
import io.qalipsis.core.head.jdbc.repository.ZoneRepository
import io.qalipsis.core.head.model.Zone
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URL

@WithMockk
internal class PersistenceZoneServiceImplTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var zoneRepository: ZoneRepository

    @InjectMockKs
    @SpyK(recordPrivateCalls = true)
    private lateinit var zoneService: PersistenceZoneServiceImpl

    @Test
    internal fun `should list the zones`() = testDispatcherProvider.run {
        // given
        val image = URL("https://images.app.goo.gl/9gtthcy5jgLjb6GW9")
        val zoneEntity1 = ZoneEntity(
            key = "EU",
            title = "zone 1",
            description = "This is eu zone",
            imagePath = image
        )
        val zoneEntity2 = ZoneEntity(
            key = "CH",
            title = "zone 2",
            description = "This is ch zone",
            imagePath = image
        )
        val zones = listOf(zoneEntity1, zoneEntity2)
        coEvery { zoneRepository.findZonesByTenant(any()) } returns zones

        // when
        val result = zoneService.list("tenant-1").toList()

        // then
        assertThat(result).all {
            hasSize(2)
            index(0).all {
                prop(Zone::key).isEqualTo("EU")
                prop(Zone::title).isEqualTo("zone 1")
                prop(Zone::description).isEqualTo("This is eu zone")
                prop(Zone::imagePath).isEqualTo(image)
                prop(Zone::enabled).isEqualTo(true)
            }
            index(1).all {
                prop(Zone::key).isEqualTo("CH")
                prop(Zone::title).isEqualTo("zone 2")
                prop(Zone::description).isEqualTo("This is ch zone")
                prop(Zone::imagePath).isEqualTo(image)
                prop(Zone::enabled).isEqualTo(true)
            }
        }
        coVerifyOrder { zoneRepository.findZonesByTenant("tenant-1") }
        confirmVerified(zoneRepository)
    }
}