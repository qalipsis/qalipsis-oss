package io.qalipsis.core.head.controller


import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.head.admin.SaveTenantDto
import io.qalipsis.core.head.admin.SaveTenantResponse
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


@MicronautTest
class TenantControllerIntegrationTest(
) {


    @Inject
    @field:Client("/")
    lateinit var httpClient : HttpClient

    @Test
    fun `should return display name and 200`() {

        val requestDto = SaveTenantDto("test")
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/api/admin/tenant", requestDto)
        val rsp: HttpResponse<SaveTenantResponse> = httpClient.toBlocking().exchange(
            createTenantRequest,
            SaveTenantResponse::class.java
        )

        assertEquals(200, rsp.status.code)
        assertEquals("test", rsp.body.get().displayName)
    }


    @Test
    fun `should not return 400`() {


        val stringLengthWith201 = "W1HEH0JP1r0BsTrKwcyxCBZmaIeDmdbQhIreDcFsJrVBIMPid6NUnFZnl8lf9MMnnupmHlnX21c1r7Snd0YSv0cYqKhcLN5hl3a8AMeAPEvBhToJeXzJeEK7c6ugPzx170fVV1HMOWaUoDEWki6B13FcHgsRYlzQtdMlFD7D2zKcUbMv3NFgk98CqLyEzBxZMAXTtN5qc"

        val requestDto = SaveTenantDto(stringLengthWith201)
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/api/admin/tenant", requestDto)
        val rsp: HttpResponse<SaveTenantResponse> = httpClient.toBlocking().exchange(
            createTenantRequest,
            SaveTenantResponse::class.java
        )

        assertEquals(400, rsp.status.code)
    }

}


