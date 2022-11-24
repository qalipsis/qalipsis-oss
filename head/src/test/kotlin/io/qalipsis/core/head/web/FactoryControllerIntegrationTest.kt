package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.excludeRecords
import io.mockk.impl.annotations.MockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.report.FactoryState
import io.qalipsis.core.head.report.WidgetService
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class FactoryControllerIntegrationTest {

    @Inject
    @field:Client("/factories")
    lateinit var httpClient: HttpClient

    @MockK
    private lateinit var widgetService: WidgetService

    @MockBean(WidgetService::class)
    fun widgetService() = widgetService

    @BeforeEach
    internal fun setUp() {
        excludeRecords { widgetService.hashCode() }
    }

    @Test
    fun `should successfully fetch the latest factory states per tenant`() {
        // given
        val factoryState = FactoryState(
            registered = 1,
            idle = 0,
            unhealthy = 2,
            offline = 2
        )
        val latestFactoryStateRequest = HttpRequest.GET<FactoryState>("/states")
        coEvery { widgetService.getFactoryStates(any()) } returns factoryState

        // when
        val response = httpClient.toBlocking().exchange(latestFactoryStateRequest, FactoryState::class.java)

        // then
        coVerifyOnce { widgetService.getFactoryStates(Defaults.TENANT) }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isEqualTo(factoryState)
        }
        confirmVerified(widgetService)
    }

}