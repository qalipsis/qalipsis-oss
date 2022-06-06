package io.qalipsis.core.head.security


import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.head.jdbc.repository.TenantRepository
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreationRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

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
}



