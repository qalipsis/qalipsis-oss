package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.UserCreationRequest
import io.qalipsis.core.head.security.AddRoleUserPatch
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.security.RemoveRoleUserPatch
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.core.head.security.User
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UsernameUserPatch
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD, "jwt"])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class UserControllerIntegrationTest {

    @Inject
    @field:Client("/users")
    private lateinit var httpClient: HttpClient

    @Inject
    private lateinit var jwtGenerator: JwtGenerator

    @RelaxedMockK
    private lateinit var userManagement: UserManagement

    @MockBean(UserManagement::class)
    fun userManagement() = userManagement

    @Test
    fun `should create the user`() {
        // given
        val now = Instant.now()
        val createdUser = User(
            tenant = "my-tenant",
            username = "my-user",
            version = now,
            creation = now,
            displayName = "just-test",
            email = "foo+111@bar.com",
            roles = listOf(RoleName.TESTER)
        )
        coEvery { userManagement.create("my-tenant", any()) } returns createdUser

        val userCreation = UserCreationRequest(
            username = "my-user",
            email = "foo+111@bar.com",
            displayName = "just-test",
            roles = listOf(RoleName.TESTER, RoleName.REPORTER)
        )
        val createUserRequest = HttpRequest.POST("/", userCreation)
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.WRITE_USER)))

        // when
        val response = httpClient.toBlocking().exchange(createUserRequest, User::class.java)

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { response.body() }.isDataClassEqualTo(createdUser)
        }

        coVerifyOnce {
            userManagement.create(
                "my-tenant", withArg {
                    assertThat(it).all {
                        prop(User::tenant).isEqualTo("my-tenant")
                        prop(User::username).isEqualTo("my-user")
                        prop(User::email).isEqualTo("foo+111@bar.com")
                        prop(User::displayName).isEqualTo("just-test")
                        prop(User::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
                    }
                }
            )
        }
    }

    @Test
    fun `should deny the user creation when the permission is missing`() {
        // given
        val now = Instant.now()
        val createdUser = User(
            tenant = "my-tenant",
            username = "my-user",
            version = now,
            creation = now,
            displayName = "just-test",
            email = "foo+111@bar.com",
            roles = listOf(RoleName.TESTER)
        )
        coEvery { userManagement.create("my-tenant", any()) } returns createdUser

        val userCreation = UserCreationRequest(
            username = "my-user",
            email = "foo+111@bar.com",
            displayName = "just-test",
            roles = listOf(RoleName.TESTER, RoleName.REPORTER)
        )
        val createUserRequest = HttpRequest.POST("/", userCreation)
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.READ_USER)))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(createUserRequest, User::class.java)
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        coVerifyNever { userManagement.create(any(), any()) }
    }

    @Test
    fun `should retrieve the user`() {
        // given
        val now = Instant.now()
        val getUser = User(
            tenant = "my-tenant",
            username = "my-user",
            email = "foo+111@bar.com",
            displayName = "just-test",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.TESTER)
        )
        coEvery { userManagement.get("my-tenant", "my-user") } returns getUser

        val getUserRequest = HttpRequest.GET<User>("/my-user")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.READ_USER)))

        // when
        val response: HttpResponse<User> = httpClient.toBlocking().exchange(
            getUserRequest,
            User::class.java
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(getUser)
        }
        coVerifyOnce {
            userManagement.get("my-tenant", "my-user")
        }
    }

    @Test
    fun `should get the user itself even without relevant permission`() {
        // given
        val now = Instant.now()
        val getUser = User(
            tenant = "my-tenant",
            username = "my-user",
            email = "foo+111@bar.com",
            displayName = "just-test",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.TESTER)
        )
        coEvery { userManagement.get("my-tenant", "my-user") } returns getUser

        val getUserRequest = HttpRequest.GET<User>("/my-user")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("my-user"))

        // when
        val response: HttpResponse<User> = httpClient.toBlocking().exchange(
            getUserRequest,
            User::class.java
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(getUser)
        }
        coVerifyOnce {
            userManagement.get("my-tenant", "my-user")
        }
    }

    @Test
    fun `should deny retrieving the user when permission is missing and not called by the user itself`() {
        // given
        val getUserRequest = HttpRequest.GET<User>("/my-user")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.WRITE_USER)))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                getUserRequest,
                User::class.java
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        coVerifyNever { userManagement.get(any(), any()) }
    }

    @Test
    fun `should return list of users`() {
        // given
        val now = Instant.now()
        val user = User(
            tenant = "my-tenant",
            username = "my-user",
            email = "foo+111@bar.com",
            displayName = "just-test",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.TESTER)
        )
        val user2 = User(
            tenant = "my-tenant",
            username = "my-tenant-2",
            email = "foo+222@bar.com",
            displayName = "just-test-2",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.REPORTER)
        )
        coEvery { userManagement.findAll("my-tenant") } returns listOf(user, user2)

        val getAllUsersRequest = HttpRequest.GET<List<User>>("/")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.READ_USER)))

        // when
        val response: HttpResponse<List<User>> = httpClient.toBlocking().exchange(
            getAllUsersRequest,
            Argument.listOf(User::class.java)
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                hasSize(2)
                index(0).isDataClassEqualTo(user)
                index(1).isDataClassEqualTo(user2)
            }
        }
        coVerifyOnce {
            userManagement.findAll("my-tenant")
        }
    }

    @Test
    fun `should deny listing the users when the permission is missing`() {
        // given
        val getUserRequest = HttpRequest.GET<User>("/my-user")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.WRITE_USER)))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                getUserRequest,
                User::class.java
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        coVerifyNever { userManagement.findAll(any()) }
    }

    @Test
    fun `should patch the user when the caller has the expected permission`() {
        // given
        val now = Instant.now()
        val getUser = User(
            tenant = "my-tenant",
            username = "my-user",
            email = "foo+111@bar.com",
            displayName = "just-test",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.TESTER)
        )
        val updatedUser = User(
            tenant = "my-tenant",
            username = "my-tenant-new",
            email = "foo+111@bar.com",
            displayName = "just-test",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.REPORTER)
        )
        val usernameUserPatch = UsernameUserPatch("username")
        val addRoleUserPatch = AddRoleUserPatch(listOf(RoleName.TESTER, RoleName.REPORTER))
        val deleteRoleUserPatch = RemoveRoleUserPatch(listOf(RoleName.BILLING_ADMINISTRATOR))
        coEvery { userManagement.get("my-tenant", "my-user") } returns getUser
        coEvery { userManagement.update("my-tenant", any(), any()) } returns updatedUser

        val updateUserRequest =
            HttpRequest.PATCH("/my-user", listOf(usernameUserPatch, addRoleUserPatch, deleteRoleUserPatch))
                .header("X-Tenant", "my-tenant")
                .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.WRITE_USER)))

        // when
        val response = httpClient.toBlocking().exchange(
            updateUserRequest,
            User::class.java
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(updatedUser)
        }
        coVerifyOnce {
            userManagement.get("my-tenant", "my-user")
            userManagement.update("my-tenant", withArg {
                assertThat(it).isDataClassEqualTo(getUser)
            }, any())
        }
    }

    @Test
    fun `should patch the user when the caller is itself even without permission`() {
        // given
        val now = Instant.now()
        val getUser = User(
            tenant = "my-tenant",
            username = "my-user",
            email = "foo+111@bar.com",
            displayName = "just-test",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.TESTER)
        )
        val updatedUser = User(
            tenant = "my-tenant",
            username = "my-tenant-new",
            email = "foo+111@bar.com",
            displayName = "just-test",
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.REPORTER)
        )
        val usernameUserPatch = UsernameUserPatch("username")
        val addRoleUserPatch = AddRoleUserPatch(listOf(RoleName.TESTER, RoleName.REPORTER))
        val deleteRoleUserPatch = RemoveRoleUserPatch(listOf(RoleName.BILLING_ADMINISTRATOR))
        coEvery { userManagement.get("my-tenant", "my-user") } returns getUser
        coEvery { userManagement.update("my-tenant", any(), any()) } returns updatedUser

        val updateUserRequest =
            HttpRequest.PATCH("/my-user", listOf(usernameUserPatch, addRoleUserPatch, deleteRoleUserPatch))
                .header("X-Tenant", "my-tenant")
                .bearerAuth(jwtGenerator.generateValidToken("my-user"))

        // when
        val response = httpClient.toBlocking().exchange(
            updateUserRequest,
            User::class.java
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(updatedUser)
        }
        coVerifyOnce {
            userManagement.get("my-tenant", "my-user")
            userManagement.update("my-tenant", withArg {
                assertThat(it).isDataClassEqualTo(getUser)
            }, any())
        }
    }

    @Test
    fun `should deny updating the user when the permission is missing`() {
        // given
        val usernameUserPatch = UsernameUserPatch("username")
        val updateUserRequest = HttpRequest.PATCH("/my-user", listOf(usernameUserPatch))
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.READ_USER)))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                updateUserRequest,
                User::class.java
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        confirmVerified(userManagement)
    }

    @Test
    fun `should delete the user`() {
        // given
        coEvery { userManagement.disable("my-tenant", "my-user") } returns Unit

        val deleteUserRequest = HttpRequest.DELETE<Void>("/my-user")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.DELETE_USER)))

        // when
        val response: HttpResponse<Unit> = httpClient.toBlocking().exchange(
            deleteUserRequest
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.NO_CONTENT)
        }
        coVerifyOnce {
            userManagement.disable("my-tenant", "my-user")
        }
    }

    @Test
    fun `should deny deleting the user when the permission is missing`() {
        // given
        coEvery { userManagement.disable("my-tenant", "my-user") } returns Unit

        val deleteUserRequest = HttpRequest.DELETE<Void>("/my-user")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin", listOf(Permissions.WRITE_USER)))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(deleteUserRequest, Unit::class.java)
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        coVerifyNever { userManagement.disable(any(), any()) }
    }
}