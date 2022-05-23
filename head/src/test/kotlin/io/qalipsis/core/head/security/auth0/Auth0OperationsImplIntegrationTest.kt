package io.qalipsis.core.head.security.auth0

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isNotIn
import assertk.assertions.prop
import com.auth0.client.mgmt.ManagementAPI
import com.auth0.exception.APIException
import com.auth0.json.mgmt.users.User
import io.aerisconsulting.catadioptre.coInvokeNoArgs
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.spyk
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.RoleEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.repository.PostgresqlTemplateTest
import io.qalipsis.core.head.jdbc.repository.RoleRepository
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

@WithMockk
@MicronautTest(
    propertySources = ["classpath:application-auth0-test.yml"],
    environments = ["auth0", ExecutionEnvironments.POSTGRESQL]
)
internal class Auth0OperationsImplIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var roleRepository: RoleRepository

    @Inject
    private lateinit var originalOperations: Auth0OperationsImpl

    private lateinit var operations: Auth0OperationsImpl

    private val createdUsers = mutableListOf<User>()

    @BeforeEach
    internal fun setUp() {
        // In order to preserve the rate limit, we slow down the access to the management API.
        operations = spyk(originalOperations, recordPrivateCalls = true)
        coEvery { operations["getManagementAPI"]() } coAnswers {
            Thread.sleep(300)
            invocation.originalCall.invoke()
        }
    }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        createdUsers.forEach { getManagementApi().users().delete(it.id).execute() }
        roleRepository.findAll().collect { getManagementApi().roles().delete(it.reference).execute() }

        roleRepository.deleteAll()
        tenantRepository.deleteAll()

        createdUsers.clear()
    }

    private suspend fun getManagementApi() = operations.coInvokeNoArgs<ManagementAPI>("getManagementAPI")

    @Test
    @Timeout(30)
    internal fun `should create and get a user`() = testDispatcherProvider.run {
        // given
        val user = createUser()

        // when
        val createdUser = operations.createUser(user).also(createdUsers::add)

        // then
        assertThat(createdUser).all {
            prop(User::getEmail).isEqualTo(user.email)
            prop(User::getUsername).isEqualTo(user.username)
            prop(User::getName).isEqualTo(user.name)
        }

        // when
        val fetched = operations.getUser(createdUser.id)

        // then
        assertThat(fetched).all {
            prop(User::getEmail).isEqualTo(user.email)
            prop(User::getUsername).isEqualTo(user.username)
            prop(User::getName).isEqualTo(user.name)
        }
    }

    @Test
    @Timeout(30)
    internal fun `should update a user`() = testDispatcherProvider.run {
        // given
        val user = createUser()
        val createdUser = operations.createUser(user).also(createdUsers::add)

        // when
        createdUser.id = createdUser.id
        createdUser.email = "aaa@bbb.com"
        operations.updateUser(createdUser)

        // then
        var fetched = operations.getUser(createdUser.id)
        assertThat(fetched).all {
            prop(User::getEmail).isEqualTo("aaa@bbb.com")
            prop(User::getUsername).isEqualTo(user.username)
            prop(User::getName).isEqualTo(user.name)
        }

        // when updating all at once.
        createdUser.id = createdUser.id
        createdUser.email = "bbb@ccc.com"
        createdUser.username = "qali-test-upd"
        createdUser.name = "The User"
        operations.updateUser(createdUser)

        // then
        fetched = operations.getUser(createdUser.id)
        assertThat(fetched).all {
            prop(User::getEmail).isEqualTo("bbb@ccc.com")
            prop(User::getUsername).isEqualTo("qali-test-upd")
            prop(User::getName).isEqualTo("The User")
        }
    }

    @Test
    @Timeout(30)
    internal fun `should delete a user`() = testDispatcherProvider.run {
        // given
        val createdUser = operations.createUser(createUser()).also(createdUsers::add)

        // when
        operations.deleteUser(createdUser.id)

        // then
        val exception = assertThrows<APIException> { operations.getUser(createdUser.id) }
        assertThat(exception).prop(APIException::getStatusCode).isEqualTo(404)
    }


    @Test
    @Timeout(30)
    internal fun `should return the ids for all in a tenant and only create the missing ones on demand`() =
        testDispatcherProvider.run {
            // given
            tenantRepository.saveAll(
                listOf(
                    TenantEntity(reference = MY_TENANT_1, displayName = MY_TENANT_1),
                    TenantEntity(reference = MY_TENANT_2, displayName = MY_TENANT_2)
                )
            ).collect()

            // when
            val firstRolesIds = operations.listRolesIds(
                tenant = MY_TENANT_1,
                roles = listOf(RoleName.TESTER, RoleName.REPORTER),
                createMissingRoles = true
            )

            // then
            assertThat(roleRepository.findAll().toList().sortedBy { it.id }).all {
                hasSize(2)
                index(0).all {
                    prop(RoleEntity::tenant).isEqualTo(MY_TENANT_1)
                    prop(RoleEntity::name).isEqualTo(RoleName.TESTER)
                    prop(RoleEntity::reference).isIn(*firstRolesIds.toTypedArray())
                }
                index(1).all {
                    prop(RoleEntity::tenant).isEqualTo(MY_TENANT_1)
                    prop(RoleEntity::name).isEqualTo(RoleName.REPORTER)
                    prop(RoleEntity::reference).isIn(*firstRolesIds.toTypedArray())
                }
            }
            var rolesReferences = roleRepository.findAll().map { it.reference }.toList().toTypedArray()
            assertThat(firstRolesIds).all {
                hasSize(2)
                containsAll(*rolesReferences)
            }
            assertThat(firstRolesIds.map { getManagementApi().roles().get(it) }).hasSize(2)

            // when searching the existing roles + 1 and creating the missing one
            assertThat(
                operations.listRolesIds(
                    MY_TENANT_1,
                    listOf(RoleName.TESTER, RoleName.REPORTER, RoleName.TENANT_ADMINISTRATOR)
                )
            ).containsOnly(*firstRolesIds.toTypedArray())

            // when searching the existing roles + 1 and creating the missing one
            val secondRolesIds = operations.listRolesIds(
                tenant = MY_TENANT_1,
                roles = listOf(RoleName.TESTER, RoleName.REPORTER, RoleName.TENANT_ADMINISTRATOR),
                createMissingRoles = true
            )

            // then
            assertThat(roleRepository.findAll().toList().sortedBy { it.id }).all {
                hasSize(3)
                index(0).all {
                    prop(RoleEntity::tenant).isEqualTo(MY_TENANT_1)
                    prop(RoleEntity::name).isEqualTo(RoleName.TESTER)
                    prop(RoleEntity::reference).isIn(*firstRolesIds.toTypedArray())
                }
                index(1).all {
                    prop(RoleEntity::tenant).isEqualTo(MY_TENANT_1)
                    prop(RoleEntity::name).isEqualTo(RoleName.REPORTER)
                    prop(RoleEntity::reference).isIn(*firstRolesIds.toTypedArray())
                }
                index(2).all {
                    prop(RoleEntity::tenant).isEqualTo(MY_TENANT_1)
                    prop(RoleEntity::name).isEqualTo(RoleName.TENANT_ADMINISTRATOR)
                    prop(RoleEntity::reference).all {
                        isNotIn(*firstRolesIds.toTypedArray())
                        isIn(*secondRolesIds.toTypedArray())
                    }
                }
            }
            rolesReferences = roleRepository.findAll().map { it.reference }.toList().toTypedArray()
            assertThat(secondRolesIds).all {
                hasSize(3)
                containsAll(*firstRolesIds.toTypedArray())
                containsAll(*rolesReferences)
            }
            assertThat(secondRolesIds.map { getManagementApi().roles().get(it) }).hasSize(3)

            // when searching the same roles in another tenant
            val firstRolesIdsForOtherTenant = operations.listRolesIds(
                tenant = MY_TENANT_2,
                roles = listOf(RoleName.TESTER, RoleName.REPORTER)
            )

            // then no new role is created
            assertThat(roleRepository.findAll().toList()).hasSize(3)
            assertThat(firstRolesIdsForOtherTenant).isEmpty()

            // when searching the same roles in another tenant, and creating them if missing
            val secondRolesIdsForOtherTenant = operations.listRolesIds(
                tenant = MY_TENANT_2,
                roles = listOf(RoleName.TESTER, RoleName.REPORTER),
                createMissingRoles = true
            )

            // then
            val rolesInSecondTenant =
                roleRepository.findByTenantAndNameIn(MY_TENANT_2, RoleName.values().toSet()).sortedBy { it.id }
            assertThat(rolesInSecondTenant).all {
                hasSize(2)
                index(0).all {
                    prop(RoleEntity::tenant).isEqualTo(MY_TENANT_2)
                    prop(RoleEntity::name).isEqualTo(RoleName.TESTER)
                    prop(RoleEntity::reference).all {
                        isNotIn(*secondRolesIds.toTypedArray())
                        isIn(*secondRolesIdsForOtherTenant.toTypedArray())
                    }
                }
                index(1).all {
                    prop(RoleEntity::tenant).isEqualTo(MY_TENANT_2)
                    prop(RoleEntity::name).isEqualTo(RoleName.REPORTER)
                    prop(RoleEntity::reference).all {
                        isNotIn(*secondRolesIds.toTypedArray())
                        isIn(*secondRolesIdsForOtherTenant.toTypedArray())
                    }
                }
            }
            assertThat(secondRolesIdsForOtherTenant).all {
                hasSize(2)
                containsOnly(*rolesInSecondTenant.map { it.reference }.toTypedArray())
            }
            assertThat(rolesInSecondTenant.map { getManagementApi().roles().get(it.reference) }).hasSize(2)
        }

    @Test
    @Timeout(30)
    internal fun `should assign and unassign roles to the user`() = testDispatcherProvider.run {
        // given
        tenantRepository.saveAll(
            listOf(
                TenantEntity(reference = MY_TENANT_1, displayName = MY_TENANT_1),
                TenantEntity(reference = MY_TENANT_2, displayName = MY_TENANT_2)
            )
        ).collect()
        val createdUser = operations.createUser(createUser()).also(createdUsers::add)
        val roleIdsInTenant1 = operations.listRolesIds(
            tenant = MY_TENANT_1,
            roles = listOf(RoleName.BILLING_ADMINISTRATOR, RoleName.TESTER, RoleName.REPORTER),
            createMissingRoles = true
        )

        // when
        operations.assignRoles(createdUser.id, roleIdsInTenant1)

        // then
        var userRoles = getManagementApi().users().listRoles(createdUser.id, null).execute().items.map { it.id }.toSet()
        assertThat(userRoles).isEqualTo(roleIdsInTenant1.toSet())

        // when adding roles of tenant 2.
        val roleIdsInTenant2 = operations.listRolesIds(
            tenant = MY_TENANT_2,
            roles = listOf(RoleName.BILLING_ADMINISTRATOR, RoleName.TESTER, RoleName.REPORTER),
            createMissingRoles = true
        )
        operations.assignRoles(createdUser.id, roleIdsInTenant2)

        // then
        userRoles = getManagementApi().users().listRoles(createdUser.id, null).execute().items.map { it.id }.toSet()
        assertThat(userRoles).containsOnly(*(roleIdsInTenant1 + roleIdsInTenant2).toTypedArray())

        // when unassigning a role in tenant 1.
        operations.unassignRoles(createdUser.id, listOf(roleIdsInTenant1.last(), roleIdsInTenant2.last()))

        // then
        userRoles = getManagementApi().users().listRoles(createdUser.id, null).execute().items.map { it.id }.toSet()
        assertThat(userRoles).containsOnly(
            *(roleIdsInTenant1.subList(0, 2) + roleIdsInTenant2.subList(
                0,
                2
            )).toTypedArray()
        )

        // when removing from tenant 2
        operations.removeFromTenant(createdUser.id, MY_TENANT_2)

        // then
        userRoles = getManagementApi().users().listRoles(createdUser.id, null).execute().items.map { it.id }.toSet()
        assertThat(userRoles).containsOnly(*roleIdsInTenant1.subList(0, 2).toTypedArray())
    }

    @Test
    @Timeout(30)
    internal fun `should list all the users with a role in a tenant`() = testDispatcherProvider.run {
        // given
        tenantRepository.saveAll(
            listOf(
                TenantEntity(reference = MY_TENANT_1, displayName = MY_TENANT_1),
                TenantEntity(reference = MY_TENANT_2, displayName = MY_TENANT_2)
            )
        ).collect()
        val roleIdsInTenant1 = operations.listRolesIds(
            tenant = MY_TENANT_1,
            roles = listOf(RoleName.TESTER, RoleName.REPORTER),
            createMissingRoles = true
        )
        val roleIdsInTenant2 = operations.listRolesIds(
            tenant = MY_TENANT_2,
            roles = listOf(RoleName.TESTER, RoleName.REPORTER),
            createMissingRoles = true
        )
        val user1 = operations.createUser(createUser()).also(createdUsers::add)
        operations.assignRoles(user1.id, roleIdsInTenant1)
        val user2 = operations.createUser(createUser()).also(createdUsers::add)
        operations.assignRoles(user2.id, roleIdsInTenant1 + roleIdsInTenant2)
        val user3 = operations.createUser(createUser()).also(createdUsers::add)
        operations.assignRoles(user3.id, roleIdsInTenant2)

        // when
        val testersOfTenant1 = operations.listUsersWithRoleInTenant(RoleName.TESTER, MY_TENANT_1).map { it.id }

        // then
        assertThat(testersOfTenant1).containsOnly(user1.id, user2.id)

        // when
        val testersOfTenant2 = operations.listUsersWithRoleInTenant(RoleName.TESTER, MY_TENANT_2).map { it.id }

        // then
        assertThat(testersOfTenant2).containsOnly(user2.id, user3.id)

        // when
        val adminsOfTenant1 =
            operations.listUsersWithRoleInTenant(RoleName.TENANT_ADMINISTRATOR, MY_TENANT_1).map { it.id }

        // then
        assertThat(adminsOfTenant1).isEmpty()
    }

    @Test
    internal fun `should throw an error when the user is an admin and there is less than 2 administrators`() =
        testDispatcherProvider.run {
            // given
            val spiedOperations = spyk(operations, recordPrivateCalls = true) {
                coEvery { listUsersWithRoleInTenant(RoleName.TENANT_ADMINISTRATOR, MY_TENANT_1) } returns listOf(
                    relaxedMockk(),
                    relaxedMockk()
                )
                coEvery { listUsersWithRoleInTenant(RoleName.BILLING_ADMINISTRATOR, MY_TENANT_1) } returns listOf<User>(
                    relaxedMockk()
                )
            }

            // when
            assertDoesNotThrow {
                spiedOperations.validateAdministrationRolesRemoval(MY_TENANT_1, listOf(RoleName.TENANT_ADMINISTRATOR))
            }
            assertThrows<IllegalArgumentException> {
                spiedOperations.validateAdministrationRolesRemoval(MY_TENANT_1, listOf(RoleName.BILLING_ADMINISTRATOR))
            }
            assertDoesNotThrow {
                spiedOperations.validateAdministrationRolesRemoval(
                    MY_TENANT_1,
                    listOf(RoleName.TESTER, RoleName.REPORTER)
                )
            }
        }

    private fun createUser(): User {
        val username = "qali-test-" + RandomStringUtils.randomAlphanumeric(5).lowercase()
        val user = User()
        user.email = "$username@bar.com"
        user.username = username
        user.setPassword(UUID.randomUUID().toString().toCharArray())
        user.name = username
        user.isEmailVerified = true
        user.isBlocked = true
        user.setVerifyEmail(false)
        return user
    }

    companion object {

        val MY_TENANT_1 = "qalipsis-ci-test-1-" + RandomStringUtils.randomAlphabetic(5).lowercase()

        val MY_TENANT_2 = "qalipsis-ci-test-2-" + RandomStringUtils.randomAlphabetic(5).lowercase()
    }
}