package io.qalipsis.core.head.security.auth0

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.exceptions.EmptyResponseException
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.exception.Auth0IdentityManagementException
import jakarta.inject.Singleton
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * There is an Auth0 implementation of [IdentityManagement] interface
 *
 * @author Palina Bril
 */

@Requires(property = "identity.manager", value = "auth0")
@Singleton
internal class Auth0IdentityManagement(
    val auth0Configuration: Auth0Configuration
) : IdentityManagement {

    protected val client = HttpClient.newBuilder().build()

    override suspend fun get(identityReference: String): UserEntity {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(auth0Configuration.baseAddress + "/auth0%7C${identityReference}?fields=username%2Cemail%2Cname&include_fields=true"))
            .GET()
            .header("authorization", "Bearer ${(auth0Configuration.token)}")
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) throw EmptyResponseException()
        if (response.statusCode() != 200) throw Auth0IdentityManagementException(response.body())
        val objectMapper = ObjectMapper().registerKotlinModule()
        val returnedModel = objectMapper.readValue(response.body(), Auth0User::class.java)
        val user = UserEntity(
            username = returnedModel.username,
            displayName = returnedModel.name,
            emailAddress = returnedModel.email
        )
        return user.copy(identityReference = identityReference)
    }

    override suspend fun save(userEntity: UserEntity): UserEntity {
        val auth0User = Auth0User(
            username = userEntity.username,
            email = userEntity.emailAddress,
            name = userEntity.displayName
        )
        val objectMapper = ObjectMapper()
        val requestBody: String = objectMapper.registerModule(JavaTimeModule()).writeValueAsString(auth0User)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(auth0Configuration.baseAddress))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .header("authorization", "Bearer ${(auth0Configuration.token)}")
            .setHeader("content-type", "application/json")
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 201) throw Auth0IdentityManagementException(response.body())
        val identityReference = response.body().split("user_id\":\"").get(1).split("\",").get(0)
        return userEntity.copy(identityReference = identityReference)
    }

    override suspend fun delete(identityReference: String) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(auth0Configuration.baseAddress + "/auth0%7C${identityReference}"))
            .DELETE()
            .header("authorization", "Bearer ${(auth0Configuration.token)}")
            .build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    override suspend fun update(user: UserEntity) {
        val requestBody1 = "{\"username\":\"${user.username}\",\"name\":\"${user.displayName}\"}"
        val requestBody2 = "{\"email\":\"${user.emailAddress}\"}"
        makeRequest(requestBody1, user)
        makeRequest(requestBody2, user)
    }

    private fun makeRequest(requestBody: String, user: UserEntity) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(auth0Configuration.baseAddress + "/auth0%7C${user.identityReference}"))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody))
            .header("authorization", "Bearer ${(auth0Configuration.token)}")
            .setHeader("content-type", "application/json")
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw Auth0IdentityManagementException(response.body())
    }

    private fun getToken(): String {
        return client.send(
            HttpRequest.newBuilder().uri(URI.create("https://dev-d7xe49-1.us.auth0.com/oauth/token"))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id=${auth0Configuration.clientId}&client_secret=${auth0Configuration.clientSecret}&audience=${auth0Configuration.apiIdentifier}"))
                .header("content-type", "application/x-www-form-urlencoded")
                .build(), HttpResponse.BodyHandlers.ofString()
        ).body().split("\",").get(0).split(":\"").get(1)
    }
}