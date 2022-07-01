package io.qalipsis.core.head.model

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD], startApplication = false)
internal class CampaignRequestValidationIntegrationTest {

    @Inject
    private lateinit var validator: Validator

    @Test
    fun `minions count should be less the maximum value`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1000001), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("minionsCount") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be less than or equal to 1000000"))
    }

    @Test
    fun `minions count should be positive`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(-1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("minionsCount") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than 0"))
    }

    @Test
    fun `campaign name should be greater then minimum value`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "ju",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("name") })
        Assertions.assertTrue(constraintViolation.first().message.equals("size must be between 3 and 300"))
    }

    @Test
    fun `campaign name should be less then maximum value`() {
        //given
        val campaignRequest = CampaignRequest(
            name = RandomStringUtils.randomAlphanumeric(301),
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("name") })
        Assertions.assertTrue(constraintViolation.first().message.equals("size must be between 3 and 300"))
    }

    @Test
    fun `scenarios must not be empty`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            scenarios = emptyMap()
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("scenarios") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must not be empty"))
    }

    @Test
    fun `speed factor should be less the maximum value`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            speedFactor = 1000.0,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(10000), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("speedFactor") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be less than or equal to 999"))
    }

    @Test
    fun `speed factor should be positive`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            speedFactor = -1.0,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("speedFactor") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than 0"))
    }

    @Test
    fun `startOffsetMs should be less the maximum value`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            startOffsetMs = 15001,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(10000), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("startOffsetMs") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be less than or equal to 15000"))
    }

    @Test
    fun `startOffsetMs should be positive`() {
        //given
        val campaignRequest = CampaignRequest(
            name = "just-test",
            startOffsetMs = -1,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignRequest)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("startOffsetMs") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }
}