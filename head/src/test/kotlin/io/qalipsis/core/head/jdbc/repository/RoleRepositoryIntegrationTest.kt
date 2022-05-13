package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.qalipsis.core.head.jdbc.entity.RoleEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.security.RoleName
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class RoleRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var roleRepository: RoleRepository

    @BeforeEach
    internal fun setUp() = testDispatcherProvider.run {
        tenantRepository.saveAll(
            listOf(
                TenantEntity(reference = TENANT_1, displayName = TENANT_1),
                TenantEntity(reference = TENANT_2, displayName = TENANT_2)
            )
        ).toList()
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        roleRepository.deleteAll()
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = roleRepository.save(RoleEntity(TENANT_1, RoleName.TESTER))

        // when
        val fetched = roleRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().all {
            prop(RoleEntity::tenant).isEqualTo(TENANT_1)
            prop(RoleEntity::name).isEqualTo(RoleName.TESTER)
            prop(RoleEntity::reference).isEqualTo("Tenant 1:tester")
        }
    }

    @Test
    fun `should not save a role on an absent tenant`() = testDispatcherProvider.run {
        // given
        assertThrows<DataAccessException> {
            roleRepository.save(RoleEntity("None", RoleName.TESTER))
        }
    }

    @Test
    fun `should not save two identical roles`() = testDispatcherProvider.run {
        // given
        roleRepository.save(RoleEntity(TENANT_1, RoleName.TESTER))
        assertThrows<DataAccessException> {
            roleRepository.save(RoleEntity(TENANT_1, RoleName.TESTER))
        }
    }

    @Test
    fun `should the roles by tenant and roles`() = testDispatcherProvider.run {
        // given
        roleRepository.save(RoleEntity(TENANT_1, RoleName.SUPER_ADMINISTRATOR))
        val reporterTenant1 = roleRepository.save(RoleEntity(TENANT_1, RoleName.REPORTER))
        val testerTenant1 = roleRepository.save(RoleEntity(TENANT_1, RoleName.TESTER))
        roleRepository.save(RoleEntity(TENANT_2, RoleName.TESTER))

        // when
        val fetched = roleRepository.findByTenantAndNameIn(TENANT_1, setOf(RoleName.REPORTER, RoleName.TESTER))

        // then
        assertThat(fetched).all {
            hasSize(2)
            containsOnly(reporterTenant1, testerTenant1)
        }
    }

    @Test
    fun `should delete the roles when the tenant is deleted`() = testDispatcherProvider.run {
        // given
        tenantRepository.save(TenantEntity(reference = "my tenant", displayName = "my tenant"))
        roleRepository.save(RoleEntity("my tenant", RoleName.SUPER_ADMINISTRATOR))
        val role1 = roleRepository.save(RoleEntity(TENANT_1, RoleName.REPORTER))
        roleRepository.save(RoleEntity("my tenant", RoleName.TESTER))
        val role2 = roleRepository.save(RoleEntity(TENANT_2, RoleName.TESTER))

        // when
        tenantRepository.deleteById(tenantRepository.findIdByReference("my tenant"))
        val fetched = roleRepository.findAll().toList()

        // then
        assertThat(fetched).all {
            hasSize(2)
            containsOnly(role1, role2)
        }
    }

    companion object {

        const val TENANT_1 = "Tenant 1"

        const val TENANT_2 = "Tenant 2"
    }

}