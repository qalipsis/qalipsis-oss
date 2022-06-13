package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.Profile
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

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

    @Test
    fun `should retrieve the user's profile`() {
        // when
        val response = httpClient.toBlocking().exchange(
            HttpRequest.GET<Unit>("/profile"),
            Profile::class.java
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                prop(Profile::user).isDataClassEqualTo(Defaults.PROFILE.user)
                prop(Profile::tenants).all {
                    hasSize(1)
                    each { it.isDataClassEqualTo(Tenant(Defaults.TENANT, "")) }
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