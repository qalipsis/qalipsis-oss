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
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.qalipsis.cluster.security.Permissions
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.Profile
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.security.QalipsisUser
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
internal class ProfileControllerIntegrationTest {

    @Inject
    @field:Client("/users")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @MockBean(HeadConfiguration::class)
    internal fun headConfiguration() = headConfiguration

    @AfterEach
    fun tearDown() {
        unmockkStatic(Instant::class)
    }

    @Test
    fun `should retrieve the user's profile`() {
        // when
        val now = Instant.now()
        mockkStatic(Instant::class)
        every { Instant.now() } returns now
        val response = httpClient.toBlocking().exchange(
            HttpRequest.GET<Unit>("/profile"),
            Argument.of(Profile::class.java, QalipsisUser::class.java)
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().isInstanceOf<Profile<QalipsisUser>>().all {
                prop(Profile<QalipsisUser>::user).isDataClassEqualTo(Defaults.PROFILE.user)
                prop(Profile<QalipsisUser>::tenants).all {
                    hasSize(1)
                    each { it.isDataClassEqualTo(Tenant(Defaults.TENANT, "", now)) }
                }
            }
        }
    }

    @Test
    fun `should retrieve the user's permissions`() {
        // when
        val response = httpClient.toBlocking().exchange(
            HttpRequest.GET<Unit>("/permissions"),
            Argument.setOf(String::class.java)
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull()
                .containsExactlyInAnyOrder(*Permissions.ALL_PERMISSIONS.toTypedArray())
        }
    }
}