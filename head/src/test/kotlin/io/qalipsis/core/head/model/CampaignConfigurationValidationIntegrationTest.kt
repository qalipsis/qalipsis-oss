/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.model

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD], startApplication = false)
internal class CampaignConfigurationValidationIntegrationTest {

    @Inject
    private lateinit var validator: Validator

    @Test
    fun `minions count should be less the maximum value`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1000001), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("minionsCount") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be less than or equal to 1000000"))
    }

    @Test
    fun `minions count should be positive`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "just-test",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(-1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("minionsCount") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than 0"))
    }

    @Test
    fun `campaign name should be greater then minimum value`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "ju",
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("name") })
        Assertions.assertTrue(constraintViolation.first().message.equals("size must be between 3 and 300"))
    }

    @Test
    fun `campaign name should be less then maximum value`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = RandomStringUtils.randomAlphanumeric(301),
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("name") })
        Assertions.assertTrue(constraintViolation.first().message.equals("size must be between 3 and 300"))
    }

    @Test
    fun `scenarios must not be empty`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "just-test",
            scenarios = emptyMap()
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("scenarios") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must not be empty"))
    }

    @Test
    fun `speed factor should be less the maximum value`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "just-test",
            speedFactor = 1000.0,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(10000), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("speedFactor") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be less than or equal to 999"))
    }

    @Test
    fun `speed factor should be positive`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "just-test",
            speedFactor = -1.0,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("speedFactor") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than 0"))
    }

    @Test
    fun `startOffsetMs should be less the maximum value`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "just-test",
            startOffsetMs = 15001,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(10000), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("startOffsetMs") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be less than or equal to 15000"))
    }

    @Test
    fun `startOffsetMs should be positive`() {
        //given
        val campaignConfiguration = CampaignConfiguration(
            name = "just-test",
            startOffsetMs = -1,
            scenarios = mutableMapOf("Scenario1" to ScenarioRequest(1), "Scenario2" to ScenarioRequest(11))
        )

        //when
        val constraintViolation = validator.validate(campaignConfiguration)

        //then
        Assertions.assertEquals(1, constraintViolation.size)
        Assertions.assertTrue(constraintViolation.first().propertyPath.any { it.name.equals("startOffsetMs") })
        Assertions.assertTrue(constraintViolation.first().message.equals("must be greater than or equal to 0"))
    }
}