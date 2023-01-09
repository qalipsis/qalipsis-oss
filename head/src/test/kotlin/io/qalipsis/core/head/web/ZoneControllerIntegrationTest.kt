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

package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.model.Zone
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.net.URL

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
class ZoneControllerIntegrationTest {

    @Inject
    @field:Client("/zones")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @MockBean(HeadConfiguration::class)
    internal fun headConfiguration() = headConfiguration

    @Test
    fun `should list all the zones`() {
        // given
        val zones = setOf(
            Zone(
                key = "fr",
                title = "France",
                description = "Western Europe country",
                image = URL("http://images-from-france.fr/logo"),
            ),
            Zone(
                key = "at",
                title = "Austria",
                description = "Central Europe country",
                image = URL("http://images-from-austria.fr/logo"),
            )
        )
        coEvery { headConfiguration.zones } returns zones
        val listRequest = HttpRequest.GET<Set<Zone>>("/")

        // when
        val response = httpClient.toBlocking().exchange(
            listRequest,
            Argument.setOf(Zone::class.java)
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body()!! }.containsAll(*zones.toTypedArray())
        }
    }
}