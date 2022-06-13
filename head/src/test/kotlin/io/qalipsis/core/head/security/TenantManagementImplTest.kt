package io.qalipsis.core.head.security


import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreationRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class TenantManagementImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    private lateinit var tenantRepository: TenantRepository

    @InjectMockKs
    private lateinit var tenantManagement: TenantManagementImpl

    @Test
    fun `should create a new tenant with specified reference`() = testDispatcherProvider.run {
        // given
        coEvery { tenantRepository.save(any()) } returnsArgument 0
        val tenantCreationRequest = TenantCreationRequest("foo", "ACME Inc")

        // when
        val createTenantResponse = tenantManagement.create(tenantCreationRequest)

        // then
        assertThat(createTenantResponse).all {
            prop(Tenant::displayName).isEqualTo("ACME Inc")
            prop(Tenant::reference).isEqualTo("foo")
            prop(Tenant::version).isNotNull()
        }
    }

    @Test
    fun `should create a new tenant with a generated reference when the provided one is blank`() =
        testDispatcherProvider.run {
            // given
            every { idGenerator.short() } returns "my-tenant"
            coEvery { tenantRepository.save(any()) } returnsArgument 0
            val tenantCreationRequest = TenantCreationRequest("   ", "ACME Inc")

            // when
            val createTenantResponse = tenantManagement.create(tenantCreationRequest)

            // then
            assertThat(createTenantResponse).all {
                prop(Tenant::displayName).isEqualTo("ACME Inc")
                prop(Tenant::reference).isEqualTo("my-tenant")
                prop(Tenant::version).isNotNull()
            }
        }

    @Test
    internal fun `should find all the tenant by their references`() = testDispatcherProvider.run {
        // given
        val version1 = Instant.now().minusMillis(23)
        val version2 = Instant.now().plusMillis(23)
        coEvery { tenantRepository.findByReferenceIn(any()) } returns listOf(
            TenantEntity(
                id = 1,
                version = version1,
                creation = relaxedMockk(),
                reference = "ref1",
                displayName = "The tenant #1",
                description = "",
                parent = null
            ),
            TenantEntity(
                id = 2,
                version = version2,
                creation = relaxedMockk(),
                reference = "ref2",
                displayName = "The tenant #2",
                description = "",
                parent = null
            )
        )

        // when
        val retrieved = tenantManagement.findAll(setOf("ref1", "ref2"))

        // then
        assertThat(retrieved).all {
            hasSize(2)
            containsOnly(
                Tenant(reference = "ref1", displayName = "The tenant #1", version = version1),
                Tenant(reference = "ref2", displayName = "The tenant #2", version = version2)
            )
        }
    }
}



