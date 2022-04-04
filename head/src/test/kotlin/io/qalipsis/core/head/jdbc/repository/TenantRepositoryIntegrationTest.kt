package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.prop
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant

internal class TenantRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var tenantRepository: TenantRepository

    val now = Instant.now()

    val tenantPrototype = TenantEntity(
        creation = now,
        reference = "my-tenant",
        displayName = "my-tenant-1",
        description = "Here I am",
    )

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        tenantRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())

        // when
        val fetched = tenantRepository.findById(saved.id)

        // then
        assertThat(fetched).all {
            prop(TenantEntity::reference).isEqualTo(saved.reference)
            prop(TenantEntity::displayName).isEqualTo(saved.displayName)
            prop(TenantEntity::description).isEqualTo(saved.description)
            prop(TenantEntity::parent).isEqualTo(saved.parent)
        }
        assertThat(fetched!!.creation.toEpochMilli() == saved.creation.toEpochMilli())
    }

    @Test
    fun `should update the version when the message is updated`() = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())

        // when
        val updated = tenantRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should delete scenario report message on deleteById`() = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())
        assertThat(tenantRepository.findAll().count()).isEqualTo(1)

        // when
        tenantRepository.deleteById(saved.id)

        // then
        assertThat(tenantRepository.findAll().count()).isEqualTo(0)
    }
}