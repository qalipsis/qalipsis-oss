package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.EmptyResultException
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignFactoryEntity
import io.qalipsis.core.head.jdbc.entity.CampaignScenarioEntity
import io.qalipsis.core.head.jdbc.entity.FactoryEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

internal class CampaignRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var factoryRepository: FactoryRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    @Inject
    private lateinit var campagnScenarioRepository: CampaignScenarioRepository

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


    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        campaignRepository.deleteAll()
        factoryRepository.deleteAll()
    }

    @Test
    internal fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = campaignRepository.save(campaignPrototype.copy())

        // when
        val fetched = campaignRepository.findById(saved.id)

        // then
        assertThat(fetched).isNotNull().isDataClassEqualTo(saved)
    }

    @Test
    internal fun `should find the ID of the running campaign`() = testDispatcherProvider.run {
        // given
        val saved = campaignRepository.save(campaignPrototype.copy())

        // when + then
        assertThrows<EmptyResultException> {
            campaignRepository.findIdByNameAndEndIsNull(saved.name)
        }

        // when
        campaignRepository.update(saved.copy(end = null))
        assertThat(campaignRepository.findIdByNameAndEndIsNull(saved.name)).isEqualTo(saved.id)
    }


    @Test
    fun `should update the version when the campaign is updated`() = testDispatcherProvider.run {
        // given
        val saved = campaignRepository.save(campaignPrototype.copy())

        // when
        val updated = campaignRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    internal fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val saved = campaignRepository.save(campaignPrototype.copy())
        val factory =
            factoryRepository.save(
                FactoryEntity(
                    "the-node-id",
                    Instant.now(),
                    "the-registration-node-id",
                    "unicast-channel",
                    "broadcast-channel"
                )
            )
        campagnScenarioRepository.save(CampaignScenarioEntity(saved.id, "the-scenario", 231))
        campaignFactoryRepository.save(CampaignFactoryEntity(saved.id, factory.id, discarded = false))
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(1)
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(1)

        // when
        campaignRepository.deleteById(saved.id)

        // then
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(0)
        assertThat(campagnScenarioRepository.findAll().count()).isEqualTo(0)
    }

    @Test
    internal fun `should close the open campaign`() = testDispatcherProvider.run {
        // given
        val alreadyClosedCampaign = campaignRepository.save(campaignPrototype.copy(end = Instant.now()))
        val openCampaign = campaignRepository.save(campaignPrototype.copy(end = null))
        val otherOpenCampaign = campaignRepository.save(campaignPrototype.copy(name = "other-campaign", end = null))

        // when
        val beforeCall = Instant.now()
        campaignRepository.close(campaignPrototype.name, ExecutionStatus.FAILED)

        // then
        assertThat(campaignRepository.findById(alreadyClosedCampaign.id)).isNotNull()
            .isDataClassEqualTo(alreadyClosedCampaign)
        assertThat(campaignRepository.findById(otherOpenCampaign.id)).isNotNull().isDataClassEqualTo(otherOpenCampaign)
        assertThat(campaignRepository.findById(openCampaign.id)).isNotNull().all {
            prop(CampaignEntity::version).isGreaterThan(openCampaign.version)
            prop(CampaignEntity::name).isEqualTo(openCampaign.name)
            prop(CampaignEntity::start).isEqualTo(openCampaign.start)
            prop(CampaignEntity::speedFactor).isEqualTo(openCampaign.speedFactor)
            prop(CampaignEntity::end).isNotNull().isGreaterThanOrEqualTo(beforeCall)
            prop(CampaignEntity::result).isEqualTo(ExecutionStatus.FAILED)
        }
    }
}