package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.prop
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.jdbc.entity.CampaignEntity
import io.qalipsis.core.head.jdbc.entity.CampaignReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportEntity
import io.qalipsis.core.head.jdbc.entity.ScenarioReportMessageEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class ScenarioReportMessageRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @Inject
    private lateinit var scenarioReportMessageRepository: ScenarioReportMessageRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    val messagePrototype = ScenarioReportMessageEntity(
        1,
        "my-step",
        "my-message-1",
        ReportMessageSeverity.INFO,
        "This is the first message"
    )

   @BeforeEach
    fun initial() = testDispatcherProvider.run {
        val campaignPrototype =
            CampaignEntity(
                "the-campaign-id",
                123.0,
                Instant.now() - Duration.ofSeconds(173),
                Instant.now(),
                ExecutionStatus.SUCCESSFUL
            )
        val campaingEntity = campaignRepository.save(campaignPrototype.copy())
        val campaignReportPrototype =
            CampaignReportEntity(
                campaingEntity.id, 1000, 990, 990, 10
            )
        val campaignReportEntity = campaignReportRepository.save(campaignReportPrototype)
        val scenarioReportPrototype =
            ScenarioReportEntity(
                campaignReportEntity.id, Instant.now().minusSeconds(900),
                Instant.now().minusSeconds(600),
                1000, 990, 990, 10,
                ExecutionStatus.SUCCESSFUL
            )
        scenarioReportRepository.save(scenarioReportPrototype)
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        scenarioReportMessageRepository.deleteAll()
    }
    @AfterAll
    fun tearDownAll() = testDispatcherProvider.run {
        scenarioReportRepository.deleteAll()
        campaignReportRepository.deleteAll()
        campaignRepository.deleteAll()
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportMessageRepository.save(messagePrototype.copy())

        // when
        val fetched = scenarioReportMessageRepository.findById(saved.id)

        // then
        assertThat(fetched).all {
            prop(ScenarioReportMessageEntity::id).isEqualTo(saved.id)
            prop(ScenarioReportMessageEntity::scenarioReportId).isEqualTo(saved.scenarioReportId)
            prop(ScenarioReportMessageEntity::stepId).isEqualTo(saved.stepId)
            prop(ScenarioReportMessageEntity::messageId).isEqualTo(saved.messageId)
            prop(ScenarioReportMessageEntity::severity).isEqualTo(saved.severity)
            prop(ScenarioReportMessageEntity::message).isEqualTo(saved.message)
        }
    }

    @Test
    fun `should update the version when the message is updated`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportMessageRepository.save(messagePrototype.copy())

        // when
        val updated = scenarioReportMessageRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should delete on delete`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportMessageRepository.save(messagePrototype.copy())
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(1)

        // when
        scenarioReportMessageRepository.deleteById(saved.id)

        // then
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(0)
    }
}