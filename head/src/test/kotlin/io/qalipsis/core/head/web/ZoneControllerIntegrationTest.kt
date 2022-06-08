package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.head.model.Zone
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD, "jwt"])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
class ZoneControllerIntegrationTest {

    @Inject
    @field:Client("/zones")
    lateinit var httpClient: HttpClient

    @Inject
    private lateinit var jwtGenerator: JwtGenerator

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @MockBean(HeadConfiguration::class)
    internal fun headConfiguration() = headConfiguration

    @Test
    fun `should list all the zones`() {
        // given
        val zone = Zone("en", "England", "description")
        coEvery { headConfiguration.zones } returns setOf(zone)
        val listRequest = HttpRequest.GET<List<Zone>>("/")
            .bearerAuth(jwtGenerator.generateValidToken("my-user", emptySet()))

        // when
        val response = httpClient.toBlocking().exchange(
            listRequest,
            Argument.listOf(Zone::class.java)
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body().get(0) }.isDataClassEqualTo(zone)
        }
    }

    @Test
    fun `shouldn't list all the zones without authentication`() {
        // given
        val zone = Zone("en", "England", "description")
        coEvery { headConfiguration.zones } returns setOf(zone)
        val listRequest = HttpRequest.GET<List<Zone>>("/")


        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                listRequest,
                Argument.listOf(Zone::class.java)
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.UNAUTHORIZED)
        coVerifyNever { headConfiguration.zones }
    }
}