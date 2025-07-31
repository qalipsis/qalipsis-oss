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

package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.cluster.security.DisabledSecurityConfiguration
import io.qalipsis.cluster.security.SecurityConfiguration
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.DefaultValuesCampaignConfiguration
import io.qalipsis.core.head.model.Stage
import io.qalipsis.core.head.model.Validation
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

internal class ConfigurationControllerIntegrationTest {

    @Nested
    @MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
    @PropertySource(
        Property(name = "micronaut.server.log-handled-exceptions", value = "true")
    )
    inner class DisabledConfigurationControllerIntegrationTest {

        @Inject
        @field:Client("/configuration")
        private lateinit var httpClient: HttpClient

        @Test
        fun `should successfully retrieve the disabled security configuration`() {
            // when
            val securityConfigurationRequest = HttpRequest.GET<Unit>("/security")
            val response =
                httpClient.toBlocking().exchange(securityConfigurationRequest, SecurityConfiguration::class.java)

            // then
            assertThat(response).all {
                transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
                transform("body") { it.body() }.isNotNull().isInstanceOf(DisabledSecurityConfiguration::class)
            }
        }
    }

    @Nested
    @MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
    @PropertySource(
        Property(name = "micronaut.server.log-handled-exceptions", value = "true")
    )
    inner class DefaultValuesCampaignConfigurationControllerIntegrationTest {

        @Inject
        @field:Client("/configuration")
        private lateinit var httpClient: HttpClient

        @Test
        fun `should successfully retrieve default values that can be used to create a new campaign`() {
            // when
            val campaignDefaultValues = HttpRequest.GET<Unit>("/campaign")
            val response =
                httpClient.toBlocking().exchange(campaignDefaultValues, DefaultValuesCampaignConfiguration::class.java)

            // then
            assertThat(response).all {
                transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
                transform("body") { it.body() }.isNotNull().isInstanceOf(DefaultValuesCampaignConfiguration::class)
                transform("body") { it.body() }.isNotNull().isDataClassEqualTo(
                    DefaultValuesCampaignConfiguration(
                        Validation(
                            maxMinionsCount = 10000,
                            maxExecutionDuration = Duration.ofHours(1),
                            maxScenariosCount = 4,
                            stage = Stage(
                                minMinionsCount = 1,
                                maxMinionsCount = 10000,
                                minResolution = Duration.ofMillis(500),
                                maxResolution = Duration.ofMinutes(5),
                                minDuration = Duration.ofSeconds(5),
                                maxDuration = Duration.ofHours(1),
                                minStartDuration = Duration.ofSeconds(5),
                                maxStartDuration = Duration.ofHours(1)
                            )
                        )
                    )
                )
            }
            assertThat(response.body()!!.validation).isNotNull().all {
                prop(Validation::maxMinionsCount).isEqualTo(10000)
                prop(Validation::maxExecutionDuration).isEqualTo(Duration.ofHours(1))
                prop(Validation::maxScenariosCount).isEqualTo(4)
            }
        }
    }

    @Nested
    @MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
    @PropertySource(
        Property(name = "campaign.configuration.validation.maxMinionsCount", value = "5000"),
        Property(name = "campaign.configuration.validation.maxExecutionDuration", value = "PT30M"),
        Property(name = "campaign.configuration.validation.maxScenariosCount", value = "2"),
        Property(name = "campaign.configuration.validation.stage.minMinionsCount", value = "1"),
        Property(name = "campaign.configuration.validation.stage.maxMinionsCount", value = "5000"),
        Property(name = "campaign.configuration.validation.stage.minResolution", value = "PT5S"),
        Property(name = "campaign.configuration.validation.stage.maxResolution", value = "PT30M"),
        Property(name = "campaign.configuration.validation.stage.minDuration", value = "PT10S"),
        Property(name = "campaign.configuration.validation.stage.maxDuration", value = "PT40M"),
        Property(name = "campaign.configuration.validation.stage.minStartDuration", value = "PT30S"),
        Property(name = "campaign.configuration.validation.stage.maxStartDuration", value = "PT20M"),
    )
    inner class DefinedAllValuesCampaignConfigurationControllerIntegrationTest {

        @Inject
        @field:Client("/configuration")
        private lateinit var httpClient: HttpClient

        @Test
        fun `should successfully retrieve defined values that can be used to create a new campaign`() {
            // when
            val campaignDefaultValues = HttpRequest.GET<Unit>("/campaign")
            val response =
                httpClient.toBlocking().exchange(campaignDefaultValues, DefaultValuesCampaignConfiguration::class.java)

            // then
            assertThat(response).all {
                transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
                transform("body") { it.body() }.isNotNull().isInstanceOf(DefaultValuesCampaignConfiguration::class)
                transform("body") { it.body() }.isNotNull().isDataClassEqualTo(
                    DefaultValuesCampaignConfiguration(
                        Validation(
                            maxMinionsCount = 5000,
                            maxExecutionDuration = Duration.ofMinutes(30),
                            maxScenariosCount = 2,
                            stage = Stage(
                                minMinionsCount = 1,
                                maxMinionsCount = 5000,
                                minResolution = Duration.ofSeconds(5),
                                maxResolution = Duration.ofMinutes(30),
                                minDuration = Duration.ofSeconds(10),
                                maxDuration = Duration.ofMinutes(40),
                                minStartDuration = Duration.ofSeconds(30),
                                maxStartDuration = Duration.ofMinutes(20)
                            )
                        )
                    )
                )
            }
            assertThat(response.body()!!.validation).isNotNull().all {
                prop(Validation::maxMinionsCount).isEqualTo(5000)
                prop(Validation::maxExecutionDuration).isEqualTo(Duration.ofMinutes(30))
                prop(Validation::maxScenariosCount).isEqualTo(2)
            }
        }
    }

    @Nested
    @MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.TRANSIENT, ExecutionEnvironments.SINGLE_HEAD])
    @PropertySource(
        Property(name = "campaign.configuration.validation.maxMinionsCount", value = "5000"),
        Property(name = "campaign.configuration.validation.maxExecutionDuration", value = "PT30M"),
        Property(name = "campaign.configuration.validation.maxScenariosCount", value = "2"),
        Property(name = "campaign.configuration.validation.stage.minMinionsCount", value = "1"),
        Property(name = "campaign.configuration.validation.stage.minResolution", value = "PT5S"),
        Property(name = "campaign.configuration.validation.stage.maxResolution", value = "PT30M"),
        Property(name = "campaign.configuration.validation.stage.minDuration", value = "PT10S")
    )
    inner class DefinedMinimalValuesCampaignConfigurationControllerIntegrationTest {

        @Inject
        @field:Client("/configuration")
        private lateinit var httpClient: HttpClient

        @Test
        fun `should successfully retrieve minimal defined values that can be used to create a new campaign`() {
            // when
            val campaignDefaultValues = HttpRequest.GET<Unit>("/campaign")
            val response =
                httpClient.toBlocking().exchange(campaignDefaultValues, DefaultValuesCampaignConfiguration::class.java)

            // then
            assertThat(response).all {
                transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
                transform("body") { it.body() }.isNotNull().isInstanceOf(DefaultValuesCampaignConfiguration::class)
                transform("body") { it.body() }.isNotNull().isDataClassEqualTo(
                    DefaultValuesCampaignConfiguration(
                        Validation(
                            maxMinionsCount = 5000,
                            maxExecutionDuration = Duration.ofMinutes(30),
                            maxScenariosCount = 2,
                            stage = Stage(
                                minMinionsCount = 1,
                                maxMinionsCount = 5000,
                                minResolution = Duration.ofSeconds(5),
                                maxResolution = Duration.ofMinutes(30),
                                minDuration = Duration.ofSeconds(10),
                                maxDuration = Duration.ofMinutes(30),
                                minStartDuration = Duration.ofSeconds(10),
                                maxStartDuration = Duration.ofMinutes(30)
                            )
                        )
                    )
                )
            }

            assertThat(response.body()!!.validation).isNotNull().all {
                prop(Validation::maxMinionsCount).isEqualTo(5000)
                prop(Validation::maxExecutionDuration).isEqualTo(Duration.ofMinutes(30))
                prop(Validation::maxScenariosCount).isEqualTo(2)
            }
        }
    }
}