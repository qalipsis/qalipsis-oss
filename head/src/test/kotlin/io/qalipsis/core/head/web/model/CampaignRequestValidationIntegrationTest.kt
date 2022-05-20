package io.qalipsis.core.head.web.model

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.campaign.CampaignManager
import io.qalipsis.core.head.factory.ClusterFactoryService
import io.qalipsis.core.head.web.CampaignController
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.validation.ConstraintViolationException

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD])
internal class CampaignRequestValidationIntegrationTest {

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
    fun `minions count should be less the maximum value`() {
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1000001), "Scenario2" to ScenarioRequest(11))
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("minionsCount: must be less than or equal to 1000000"))
    }

    @Test
    fun `minions count should be positive`() {
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
    fun `campaign name should be greater then minimum value`() {
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
    fun `campaign name should be less then maximum value`() {
        val campaignRequest = CampaignRequest(
            name = RandomStringUtils.randomAlphanumeric(301),
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
    fun `scenarios must not be empty`() {
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

    @Test
    fun `speed factor should be less the maximum value`() {
        val campaignRequest = CampaignRequest(
            name = "just-test",
            speedFactor = 1000.0,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1000001), "Scenario2" to ScenarioRequest(11))
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("speedFactor: must be less than or equal to 999"))
    }

    @Test
    fun `speed factor should be positive`() {
        val campaignRequest = CampaignRequest(
            name = "just-test",
            speedFactor = -1.0,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("speedFactor: must be greater than 0"))
    }

    @Test
    fun `startOffsetMs should be less the maximum value`() {
        val campaignRequest = CampaignRequest(
            name = "just-test",
            startOffsetMs = 15001,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1000001), "Scenario2" to ScenarioRequest(11))
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("startOffsetMs: must be less than or equal to 15000"))
    }

    @Test
    fun `startOffsetMs should be positive`() {
        val campaignRequest = CampaignRequest(
            name = "just-test",
            startOffsetMs = -1,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )
        val exception = assertThrows(ConstraintViolationException::class.java) {
            runBlocking {
                campaignController.execute("qalipsis", campaignRequest)
            }
        }
        assertTrue(exception.message!!.contains("startOffsetMs: must be greater than or equal to 0"))
    }
}