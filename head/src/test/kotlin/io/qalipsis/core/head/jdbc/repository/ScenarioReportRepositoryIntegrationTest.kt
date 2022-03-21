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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class ScenarioReportRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var campaignReportRepository: CampaignReportRepository

    @Inject
    private lateinit var scenarioReportRepository: ScenarioReportRepository

    @Inject
    private lateinit var scenarioReportMessageRepository: ScenarioReportMessageRepository

    @Inject
    private lateinit var campaignRepository: CampaignRepository

    private val scenarioReportPrototype =
        ScenarioReportEntity(
            campaignReportId = 1,
            start = Instant.now().minusSeconds(900),
            end = Instant.now().minusSeconds(600),
            startedMinions = 1000,
            completedMinions = 990,
            successfulExecutions = 990,
            failedExecutions = 10,
            ExecutionStatus.SUCCESSFUL
        )


    @BeforeEach
    fun initial() = testDispatcherProvider.run {
        val campaignPrototype =
            CampaignEntity(
                campaignId = "the-campaign-id",
                speedFactor = 123.0,
                start = Instant.now() - Duration.ofSeconds(173),
                end = Instant.now(),
                result = ExecutionStatus.SUCCESSFUL
            )
        val campaingEntity = campaignRepository.save(campaignPrototype.copy())
        val campaignReportPrototype =
            CampaignReportEntity(
                campaingEntity.id, 1000, 990, 990, 10
            )
        campaignReportRepository.save(campaignReportPrototype)
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
        val saved = scenarioReportRepository.save(scenarioReportPrototype.copy())

        // when
        val fetched = scenarioReportRepository.findById(saved.id)

        // then
        assertThat(fetched).all {
            prop(ScenarioReportEntity::id).isEqualTo(saved.id)
            prop(ScenarioReportEntity::campaignReportId).isEqualTo(saved.campaignReportId)
            prop(ScenarioReportEntity::startedMinions).isEqualTo(saved.startedMinions)
            prop(ScenarioReportEntity::completedMinions).isEqualTo(saved.completedMinions)
            prop(ScenarioReportEntity::successfulExecutions).isEqualTo(saved.successfulExecutions)
            prop(ScenarioReportEntity::failedExecutions).isEqualTo(saved.failedExecutions)
            prop(ScenarioReportEntity::messages).isEqualTo(saved.messages)
            prop(ScenarioReportEntity::status).isEqualTo(saved.status)
        }
        scenarioReportRepository.delete(saved)
    }

    @Test
    fun `should update the version when the scenario report is updated`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportRepository.save(scenarioReportPrototype.copy())

        // when
        val updated = scenarioReportRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
        scenarioReportRepository.delete(updated)
    }

    @Test
    fun `should delete all the sub-entities on delete`() = testDispatcherProvider.run {
        // given
        val saved = scenarioReportRepository.save(scenarioReportPrototype.copy())
        val messagePrototype = ScenarioReportMessageEntity(
            scenarioReportId = saved.id,
            stepId = "my-step",
            messageId = "my-message-1",
            severity = ReportMessageSeverity.INFO,
            message = "This is the first message"
        )
        scenarioReportMessageRepository.save(messagePrototype.copy())
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(1)
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(1)

        // when
        scenarioReportRepository.deleteById(saved.id)

        // then
        assertThat(scenarioReportRepository.findAll().count()).isEqualTo(0)
        assertThat(scenarioReportMessageRepository.findAll().count()).isEqualTo(0)
    }
}