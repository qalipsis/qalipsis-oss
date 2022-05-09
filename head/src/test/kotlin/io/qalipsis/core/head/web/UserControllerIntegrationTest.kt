package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.security.CreateUserPatch
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.entity.QalipsisUser
import io.qalipsis.core.head.security.entity.RoleName
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
internal class UserControllerIntegrationTest {

    @Inject
    @field:Client("/users")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var userManagement: UserManagement

    @MockBean(UserManagement::class)
    fun userManagement() = userManagement

    @Test
    fun `should return the user after save`() {
        // given
        val now = Instant.now()
        val createdUser = QalipsisUser(
            username = "qalipsis-test",
            email = "foo+111@bar.com",
            name = "just-test",
            userEntityId = 5,
            version = now,
            creation = now,
            identityReference = "115",
            roles = mutableListOf(RoleName.TESTER)
        )
        coEvery { userManagement.create("qalipsis", any()) } returns createdUser

        val userCreation = QalipsisUser(
            username = "qalipsis-test",
            email = "foo+111@bar.com",
            name = "just-test",
            userEntityId = -1,
            version = now,
            creation = now,
            roles = mutableListOf(RoleName.TESTER)
        )
        val createUserRequest = HttpRequest.POST("/", userCreation).header("X-Tenant", "qalipsis")

        // when
        val response = httpClient.toBlocking().exchange(
            createUserRequest,
            QalipsisUser::class.java
        )

        // then
        coVerifyOnce {
            userManagement.create("qalipsis", any())
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(createdUser)
        }
    }

    @Test
    fun `should get the user`() {
        // given
        val now = Instant.now()
        val getUser = QalipsisUser(
            username = "qalipsis-test",
            email = "foo+111@bar.com",
            name = "just-test",
            userEntityId = 5,
            version = now,
            creation = now,
            identityReference = "115",
            roles = mutableListOf(RoleName.TESTER)
        )
        coEvery { userManagement.get("qalipsis", "qalipsis-test") } returns getUser

        val getUserRequest = HttpRequest.GET<QalipsisUser>("/qalipsis-test").header("X-Tenant", "qalipsis")

        // when
        val response: HttpResponse<QalipsisUser> = httpClient.toBlocking().exchange(
            getUserRequest,
            QalipsisUser::class.java
        )

        // then
        coVerifyOnce {
            userManagement.get("qalipsis", "qalipsis-test")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(getUser)
        }
    }

    @Test
    fun `should return list of users`() {
        // given
        val now = Instant.now()
        val user = QalipsisUser(
            username = "qalipsis-test",
            email = "foo+111@bar.com",
            name = "just-test",
            userEntityId = 5,
            version = now,
            creation = now,
            identityReference = "115",
            roles = mutableListOf(RoleName.TESTER)
        )
        val user2 = QalipsisUser(
            username = "qalipsis-2",
            email = "foo+222@bar.com",
            name = "just-test-2",
            userEntityId = 6,
            version = now,
            creation = now,
            identityReference = "225",
            roles = mutableListOf(RoleName.REPORTER)
        )
        coEvery { userManagement.getAll("qalipsis") } returns listOf(user, user2)

        val getAllUsersRequest = HttpRequest.GET<List<QalipsisUser>>("/").header("X-Tenant", "qalipsis")

        // when
        val response: HttpResponse<List<QalipsisUser>> = httpClient.toBlocking().exchange(
            getAllUsersRequest,
            Argument.listOf(QalipsisUser::class.java)
        )

        // then
        coVerifyOnce {
            userManagement.getAll("qalipsis")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.hasSize(2)
            transform("body") { it.body().get(0) }.isDataClassEqualTo(user)
            transform("body") { it.body().get(1) }.isDataClassEqualTo(user2)
        }
    }

    @Test
    fun `should return the user after update`() {
        // given
        val now = Instant.now()
        val getUser = QalipsisUser(
            username = "qalipsis-test",
            email = "foo+111@bar.com",
            name = "just-test",
            userEntityId = 5,
            version = now,
            creation = now,
            identityReference = "115",
            roles = mutableListOf(RoleName.TESTER)
        )
        val updatedUser = QalipsisUser(
            username = "qalipsis-new",
            email = "foo+111@bar.com",
            name = "just-test",
            userEntityId = 5,
            version = now,
            creation = now,
            identityReference = "115",
            roles = mutableListOf(RoleName.REPORTER)
        )
        val usernameUserPatch = CreateUserPatch("qalipsis-new", "username")
        val addRoleUserPatch = CreateUserPatch("reporter", "addRole")
        val deleteRoleUserPatch = CreateUserPatch("tester", "deleteRole")
        coEvery { userManagement.get("qalipsis", "qalipsis-test") } returns getUser
        coEvery { userManagement.save("qalipsis", any(), any()) } returns updatedUser

        val updateUserRequest = HttpRequest.PATCH(
            "/qalipsis-test", listOf(usernameUserPatch, addRoleUserPatch, deleteRoleUserPatch)
        )
            .header("X-Tenant", "qalipsis")

        // when
        val response = httpClient.toBlocking().exchange(
            updateUserRequest,
            QalipsisUser::class.java
        )

        // then
        coVerifyOnce {
            userManagement.get("qalipsis", "qalipsis-test")
            userManagement.save("qalipsis", withArg {
                assertThat(it).isDataClassEqualTo(getUser)
            }, any())
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(updatedUser)
        }
    }

    @Test
    fun `should delete the user`() {
        // given
        coEvery { userManagement.delete("qalipsis", "qalipsis-test") } returns Unit

        val deleteUserRequest = HttpRequest.DELETE<Void>("/qalipsis-test").header("X-Tenant", "qalipsis")

        // when
        val response: HttpResponse<QalipsisUser> = httpClient.toBlocking().exchange(
            deleteUserRequest
        )

        // then
        coVerifyOnce {
            userManagement.delete("qalipsis", "qalipsis-test")
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
        }
    }
}