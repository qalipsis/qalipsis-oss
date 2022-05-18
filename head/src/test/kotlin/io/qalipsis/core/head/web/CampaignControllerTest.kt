package io.qalipsis.core.head.web

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.factory.ClusterFactoryService
import io.qalipsis.core.head.jdbc.repository.ScenarioRepository
import io.qalipsis.core.head.web.entity.CampaignConfigurationConverter
import io.qalipsis.core.head.web.entity.CampaignRequest
import io.qalipsis.core.head.web.entity.ScenarioRequest
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.validation.ConstraintViolationException

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD])
internal class CampaignControllerTest {

    @Inject
    lateinit var campaignController: CampaignController

    @RelaxedMockK
    private lateinit var campaignManager: CampaignManager

    @RelaxedMockK
    private lateinit var clusterFactoryService: ClusterFactoryService

    @RelaxedMockK
    private lateinit var campaignConfigurationConverter: CampaignConfigurationConverter

    @MockBean(ClusterFactoryService::class)
    fun clusterFactoryService() = clusterFactoryService

    @MockBean(CampaignManager::class)
    fun campaignManager() = campaignManager

    @MockBean(CampaignConfigurationConverter::class)
    fun campaignConfigurationConverter() = campaignConfigurationConverter

    @Test
    fun testThatMinionsCountIsValidated() {
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(-1), "Scenario2" to ScenarioRequest(11))
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("minionsCount: must be greater than 0"))
    }

    @Test
    fun testThatCampaignNameIsValidated() {
        val campaignRequest = CampaignRequest(
            name = "ju",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("name: size must be between 3 and 300"))
    }

    @Test
    fun testThatScenariosIsValidated() {
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = emptyMap()
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("scenarios: must not be empty"))
    }
}