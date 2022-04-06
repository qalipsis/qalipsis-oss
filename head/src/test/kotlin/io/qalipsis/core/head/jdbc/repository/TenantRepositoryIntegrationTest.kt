package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioEntity
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
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
    fun `should delete scenario report message on deleteById`(
        factoryRepository: FactoryRepository,
        campaignRepository: CampaignRepository,
        scenarioRepository: ScenarioRepository
    ) = testDispatcherProvider.run {
        // given
        val saved = tenantRepository.save(tenantPrototype.copy())
        campaignRepository.save(
            CampaignEntity(
                "the-campaign-id",
                123.0,
                Instant.now() - Duration.ofSeconds(173),
                Instant.now(),
                ExecutionStatus.SUCCESSFUL,
                saved.id
            )
        )
        val factory = factoryRepository.save(
            FactoryEntity(
                nodeId = "the-node",
                registrationTimestamp = Instant.now(),
                registrationNodeId = "test",
                unicastChannel = "unicast-channel",
                tenantId = saved.id
            )
        )
        scenarioRepository.save(ScenarioEntity(factory.id, "test", 1))

        assertThat(tenantRepository.findAll().count()).isEqualTo(1)
        assertThat(scenarioRepository.findAll().count()).isEqualTo(1)
        assertThat(campaignRepository.findAll().count()).isEqualTo(1)
        assertThat(factoryRepository.findAll().count()).isEqualTo(1)

        // when
        tenantRepository.deleteById(saved.id)

        // then
        assertThat(tenantRepository.findAll().count()).isEqualTo(0)
        assertThat(scenarioRepository.findAll().count()).isEqualTo(0)
        assertThat(campaignRepository.findAll().count()).isEqualTo(0)
        assertThat(factoryRepository.findAll().count()).isEqualTo(0)
    }
}