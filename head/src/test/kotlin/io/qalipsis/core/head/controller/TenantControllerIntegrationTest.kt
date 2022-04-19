package io.qalipsis.core.head.controller


import com.google.gson.Gson
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.head.admin.SaveTenantResponse
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


@MicronautTest
class TenantControllerIntegrationTest(
    private val client: OkHttpClient,
    private val gson: Gson
) {

    private val mediaType: MediaType = "application/json".toMediaType()


    @Test
    fun `should return display name`() {


        @Language("JSON") val body = """{
  "displayName": "test"
}"""
            .toRequestBody(mediaType)
        val request: Request = Request.Builder()
            .url("api/admin/tenant")
            .method("POST", body)
            .addHeader("Content-Type", "application/json")
            .build()
        val response: Response = client.newCall(request).execute()

        val responseObject = gson.fromJson(response.body!!.string(), SaveTenantResponse::class.java)

        Assertions.assertEquals("test", responseObject.displayName);

    }


    @Test
    fun `should not return 400`() {


        @Language("JSON") val body = """{
  "displayName": "Re4ONd4UDqGDmLy1fmHAHDchkcvcknUXKDFu1pJfglRbw4bsxk74QaFqDSWOJj5zKAmPGiiHmFFbNlXMgzKQgv0IVLw7b4qh4F8wgnkNNt7t6uLhm020RLDOsUVNcfcwN1LsXFBfLOQK8fwUrwPWGn8YYybOmxfgdpmZwpMjsrjmU7N1AnhSBJoSmZPOiK91vnQyIH2dV"
}"""
            .toRequestBody(mediaType)
        val request: Request = Request.Builder()
            .url("api/admin/tenant")
            .method("POST", body)
            .addHeader("Content-Type", "application/json")
            .build()
        val response: Response = client.newCall(request).execute()

        Assertions.assertEquals("400", response.code);

    }

}


