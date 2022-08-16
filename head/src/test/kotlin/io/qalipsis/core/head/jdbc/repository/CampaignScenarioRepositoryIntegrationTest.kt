package io.qalipsis.core.head.jdbc.repository

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.TenantEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * @author Joël Valère
 */
internal class CampaignScenarioRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var tenantRepository: TenantRepository

    @Inject
    private lateinit var userRepository: UserRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignScenarioRepository: CampaignScenarioRepository

    private val campaignPrototype =
        CampaignEntity(
            key = "the-campaign-id",
            name = "This is my new campaign",
            speedFactor = 123.0,
            start = Instant.now() - Duration.ofSeconds(173),
            end = Instant.now(),
            result = ExecutionStatus.SUCCESSFUL,
            configurer = 1L // Default user.
        )

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        tenantRepository.deleteAll()
        kotlin.runCatching {
            val allButDefaultUsers = userRepository.findAll().filterNot { it.username == Defaults.USER }.toList()
            if (allButDefaultUsers.isNotEmpty()) {
                userRepository.deleteAll(allButDefaultUsers)
            }
        }
    }

    @Test
    internal fun `should return campaign scenario name by scenario patterns names and campaign keys`() = testDispatcherProvider.run {
        //given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
        val saved = campaignRepository.save(campaignPrototype.copy(key = "key-1", name = "campaign-1", end = null, tenantId = tenant.id))
        val saved2 = campaignRepository.save(campaignPrototype.copy(key = "key-2", name = "campaign-2", end = null, tenantId = tenant.id))
        val saved3 = campaignRepository.save(campaignPrototype.copy(key = "key-3", name = "campaign-3", end = null, tenantId = tenant.id))
        campaignRepository.save(campaignPrototype.copy(key = "key-4", name = "campaign-4", end = null, tenantId = tenant2.id))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", 231))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "SCENARIO-2", 232))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved2.id, "scenario-3", 233))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved3.id, "SCenaRIo", 234))

        //when + then
        assertThat(campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(listOf("%sce%"), listOf("key-2"))).containsOnly("scenario-3")
        assertThat(campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(listOf("%sce%"), listOf("key-3", "key-2"))).containsOnly("scenario-3", "SCenaRIo")
        assertThat(campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(listOf("%sce%"), listOf("key-3", "key-2", "key-1"))).containsOnly("scenario-3", "SCenaRIo", "SCENARIO-2", "the-scenario")
        assertThat(campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(listOf("sce%"), listOf("key-3", "key-2", "key-1"))).containsOnly("scenario-3", "SCenaRIo", "SCENARIO-2")
        assertThat(campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(listOf("%rio-_"), listOf("key-3", "key-2", "key-1"))).containsOnly("scenario-3", "SCENARIO-2")
        assertThat(campaignScenarioRepository.findNameByNamePatternsAndCampaignKeys(listOf("%rio-_"), listOf("key-3"))).isEmpty()
    }

    @Test
    internal fun `should return campaign scenario name by campaign keys`() = testDispatcherProvider.run {
        //given
        val tenant = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref", "my-tenant"))
        val tenant2 = tenantRepository.save(TenantEntity(Instant.now(), "tenant-ref2", "my-tenant2"))
        val saved = campaignRepository.save(campaignPrototype.copy(key = "key-1", name = "campaign-1", end = null, tenantId = tenant.id))
        val saved2 = campaignRepository.save(campaignPrototype.copy(key = "key-2", name = "campaign-2", end = null, tenantId = tenant.id))
        campaignRepository.save(campaignPrototype.copy(key = "key-4", name = "campaign-4", end = null, tenantId = tenant2.id))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", 231))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "SCENARIO-2", 232))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved2.id, "scenario-3", 233))
        campaignScenarioRepository.save(CampaignScenarioEntity(saved.id, "SCenaRIo", 234))

        //when + then
        assertThat(campaignScenarioRepository.findNameByCampaignKeys(listOf("key-1"))).containsOnly("SCenaRIo", "SCENARIO-2", "the-scenario")
        assertThat(campaignScenarioRepository.findNameByCampaignKeys(listOf("key-2", "key-1"))).containsOnly("scenario-3", "SCenaRIo", "SCENARIO-2", "the-scenario")
        assertThat(campaignScenarioRepository.findNameByCampaignKeys(listOf("key-2"))).containsOnly("scenario-3")
    }
}