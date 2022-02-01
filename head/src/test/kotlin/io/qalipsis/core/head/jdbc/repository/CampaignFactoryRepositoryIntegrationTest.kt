package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class CampaignFactoryRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campaignFactoryRepository: CampaignFactoryRepository

    private val campaignPrototype =
        CampaignEntity(
            "the-campaign-id",
            123.0,
            Instant.now() - Duration.ofSeconds(173),
            Instant.now(),
            ExecutionStatus.SUCCESSFUL
        )

    private val factoryPrototype =
        FactoryEntity("the-node-id", Instant.now(), "the-registration-node-id", "unicast-channel", "broadcast-channel")

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        factoryRepository.deleteAll()
    }

    @Test
    internal fun `should save then update the discarded flag`() = testDispatcherProvider.run {
        // given
        val campaign = campaignRepository.save(campaignPrototype.copy())
        val factory1 = factoryRepository.save(factoryPrototype.copy())
        val factory2 = factoryRepository.save(factoryPrototype.copy(nodeId = "other-factory"))
        val saved1 = campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory1.id, discarded = false))
        val saved2 = campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory2.id, discarded = false))

        // when
        var fetched = campaignFactoryRepository.findById(saved1.id)

        // then
        assertThat(fetched).isNotNull().isDataClassEqualTo(saved1)

        // when
        campaignFactoryRepository.discard(campaign.id, listOf(factory1.id))

        // then
        fetched = campaignFactoryRepository.findById(saved1.id)
        assertThat(fetched).isNotNull().all {
            prop(CampaignFactoryEntity::discarded).isTrue()
            prop(CampaignFactoryEntity::version).isGreaterThan(saved1.version)
        }
        // The record for factory 2 remains unchanged.
        fetched = campaignFactoryRepository.findById(saved2.id)
        assertThat(fetched).isNotNull().all {
            prop(CampaignFactoryEntity::discarded).isFalse()
            prop(CampaignFactoryEntity::version).isEqualTo(saved2.version)
        }
    }

    @Test
    fun `should update the version when the entity is updated`() = testDispatcherProvider.run {
        // given
        val campaign = campaignRepository.save(campaignPrototype.copy())
        val factory = factoryRepository.save(factoryPrototype.copy())
        val saved = campaignFactoryRepository.save(CampaignFactoryEntity(campaign.id, factory.id, discarded = false))

        // when
        val updated = campaignFactoryRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

}