package io.qalipsis.core.head.controller


import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.qalipsis.core.head.admin.SaveTenantDto
import io.qalipsis.core.head.admin.SaveTenantResponse
import io.qalipsis.core.head.jdbc.repository.PostgresqlTemplateTest
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal class TenantControllerIntegrationTest : PostgresqlTemplateTest() {


    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    @Inject
    private lateinit var tenantRepository: TenantRepository


    @Test
    fun `should return display name and 200`() = testDispatcherProvider.run {

        val tenantDisplayName = "test"
        val requestDto = SaveTenantDto(tenantDisplayName)
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/api/admin/tenants", requestDto)
        val rsp: HttpResponse<SaveTenantResponse> = httpClient.toBlocking().exchange(
            createTenantRequest,
            SaveTenantResponse::class.java
        )

//        val fetched = tenantRepository.findByReference(rsp.body.get().reference)

        assertEquals(HttpStatus.OK.code, rsp.status.code)
        assertEquals(tenantDisplayName, rsp.body.get().displayName)

//        assertEquals(rsp.body.get().reference, fetched.reference)
//        assertEquals(rsp.body.get().version, fetched.version)
//        assertEquals(rsp.body.get().displayName, fetched.displayName)

    }


    @Test
    fun `should return 400 long displayname`() = testDispatcherProvider.run {


        val stringLengthWith201 =
            "W1HEH0JP1r0BsTrKwcyxCBZmaIeDmdbQhIreDcFsJrVBIMPid6NUnFZnl8lf9MMnnupmHlnX21c1r7Snd0YSv0cYqKhcLN5hl3a8AMeAPEvBhToJeXzJeEK7c6ugPzx170fVV1HMOWaUoDEWki6B13FcHgsRYlzQtdMlFD7D2zKcUbMv3NFgk98CqLyEzBxZMAXTtN5qc"

        val requestDto = SaveTenantDto(stringLengthWith201)
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/api/admin/tenants", requestDto)
        val rsp: HttpResponse<SaveTenantResponse> = httpClient.toBlocking().exchange(
            createTenantRequest,
            SaveTenantResponse::class.java
        )

        assertEquals(400, rsp.status.code)
    }

    @Test
    fun `should return 400 0 length displayname`() = testDispatcherProvider.run {


        val stringLengthWith201 = ""

        val requestDto = SaveTenantDto(stringLengthWith201)
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/api/admin/tenants", requestDto)
        val rsp: HttpResponse<SaveTenantResponse> = httpClient.toBlocking().exchange(
            createTenantRequest,
            SaveTenantResponse::class.java
        )

        assertEquals(400, rsp.status.code)
    }

    @Test
    fun `should return 400 blank displayname`() = testDispatcherProvider.run {

        val requestDto = Object()
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/api/admin/tenants", requestDto)
        val rsp: HttpResponse<SaveTenantResponse> = httpClient.toBlocking().exchange(
            createTenantRequest,
            SaveTenantResponse::class.java
        )

        assertEquals(400, rsp.status.code)
    }

}


